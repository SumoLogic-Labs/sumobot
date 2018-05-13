/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sumologic.sumobot.core

import java.io.File

import akka.actor._
import com.sumologic.sumobot.core.Receptionist.{RtmStateRequest, RtmStateResponse}
import com.sumologic.sumobot.core.model.{IncomingMessageAttachment, _}
import com.sumologic.sumobot.plugins.BotPlugin.{InitializePlugin, PluginAdded, PluginRemoved}
import slack.api.{BlockingSlackApiClient, SlackApiClient}
import slack.models.{ActionField, Attachment, BotMessage, ImOpened, Message, MessageChanged, User}
import slack.rtm.{RtmState, SlackRtmClient}

import scala.concurrent.ExecutionContext.Implicits.global

object Receptionist {

  case class RtmStateRequest(sendTo: ActorRef)

  case class RtmStateResponse(rtmState: RtmState)

  def props(rtmClient: SlackRtmClient,
            syncClient: BlockingSlackApiClient,
            asyncClient: SlackApiClient,
            brain: ActorRef): Props =
    Props(classOf[Receptionist], rtmClient, syncClient, asyncClient, brain)
}

class Receptionist(rtmClient: SlackRtmClient,
                   syncClient: BlockingSlackApiClient,
                   asyncClient: SlackApiClient,
                   brain: ActorRef) extends Actor with ActorLogging {

  implicit val system = ActorSystem("slack")

  private val selfId = rtmClient.state.self.id
  private val selfName = rtmClient.state.self.name
  rtmClient.addEventListener(self)

  private val atMention = """<@(\w+)>:(.*)""".r
  private val atMentionWithoutColon = """<@(\w+)>\s(.*)""".r
  private val simpleNamePrefix = """(\w+)\:?\s(.*)""".r
  private val tsPattern = "(\\d+)\\.(\\d+)".r

  private val messageAgeLimitMillis = 60 * 1000

  private var pendingIMSessionsByUserId = Map[String, (ActorRef, AnyRef)]()

  private val pluginRegistry = context.system.actorOf(Props(classOf[PluginRegistry]), "plugin-registry")

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[OutgoingMessage])
    context.system.eventStream.subscribe(self, classOf[OutgoingImage])
    context.system.eventStream.subscribe(self, classOf[OpenIM])
    context.system.eventStream.subscribe(self, classOf[RtmStateRequest])
  }


  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(self)
  }

  override def receive: Receive = {

    case message@PluginAdded(plugin, _) =>
      plugin ! InitializePlugin(rtmClient.state, brain, pluginRegistry)
      pluginRegistry ! message

    case message@PluginRemoved(_) =>
      pluginRegistry ! message

    case OutgoingMessage(channel, text) =>
      log.info(s"sending - ${channel.name}: $text")
      rtmClient.sendMessage(channel.id, text)

    case OutgoingImage(channel, imageFile, contentType, title, comment) =>
      log.info(s"Sending image (${imageFile.length()} bytes) to ${channel.name}")
      val fut = asyncClient.uploadFile(content = Left(imageFile), filetype = Some(contentType), title = Some(title),
        filename = Some(imageFile.getName), channels = Some(Seq(channel.name)), initialComment = comment)
      fut.onComplete {
        f => log.info(s"Sending image ended with $f")
      }

    case ImOpened(user, channel) =>
      pendingIMSessionsByUserId.get(user).foreach {
        tpl =>
          tpl._1 ! tpl._2
          pendingIMSessionsByUserId = pendingIMSessionsByUserId - user
      }

    case OpenIM(userId, doneRecipient, doneMessage) =>
      asyncClient.openIm(userId)
      pendingIMSessionsByUserId = pendingIMSessionsByUserId + (userId -> (doneRecipient, doneMessage))

    case message: Message if !tooOld(message.ts, message) =>
      translateAndDispatch(message.channel, message.user, message.text)

    case messageChanged: MessageChanged if !tooOld(messageChanged.ts, messageChanged) =>
      val message = messageChanged.message
      translateAndDispatch(messageChanged.channel, message.user, message.text)

    case botMessage: BotMessage if !tooOld(botMessage.ts, botMessage) && botMessage.username.isDefined =>
        translateAndDispatch(botMessage.channel, botMessage.username.get, botMessage.text, fromBot = true, botMessage.attachments)

    case RtmStateRequest(sendTo) =>
      sendTo ! RtmStateResponse(rtmClient.state)
  }

  protected def translateMessage(channelId: String, from: Sender, text: String, attachments: Seq[IncomingMessageAttachment]): IncomingMessage = {
    val channel = Channel.forChannelId(rtmClient.state, channelId)

    text match {
      case atMention(user, text) if user == selfId =>
        IncomingMessage(text.trim, true, channel, from, attachments)
      case atMentionWithoutColon(user, text) if user == selfId =>
        IncomingMessage(text.trim, true, channel, from, attachments)
      case simpleNamePrefix(name, text) if name.equalsIgnoreCase(selfName) =>
        IncomingMessage(text.trim, true, channel, from, attachments)
      case _ =>
        IncomingMessage(text.trim, channel.isInstanceOf[InstantMessageChannel], channel, from, attachments)
    }
  }

  private def translateAndDispatch(channelId: String, userId: String, text: String, fromBot: Boolean = false, attachments: Seq[Attachment] = Seq()): Unit = {
    val sentBy: Sender = if (!fromBot) {
      val slackUser: slack.models.User = rtmClient.state.users.find(_.id == userId).
        getOrElse(throw new IllegalStateException(s"Message from unknown user: $userId"))
      UserSender(slackUser)
    } else {
      BotSender(userId)
    }
    val msgToBot = translateMessage(channelId, sentBy, text, attachments.map(a => IncomingMessageAttachment(a.text.getOrElse(""))))
    if (userId != selfId) {
      log.info(s"Dispatching message: $msgToBot")
      context.system.eventStream.publish(msgToBot)
    }
  }

  // NOTE(stefan, 2017-01-09): This check is required because I've seen the API send us some old messages
  // at times that I can't explain to myself.
  private def tooOld(ts: String, message: AnyRef): Boolean = {
    ts match {
      case tsPattern(timeOfMessage, _) =>
        val oldestAllowableMessageTime = (System.currentTimeMillis() - messageAgeLimitMillis)/1000
        val messageTooOld = timeOfMessage.toLong < oldestAllowableMessageTime
        if (messageTooOld) {
          log.warning(s"Discarding old message ($ts is too old, cutoff is $oldestAllowableMessageTime): $message")
        }
        messageTooOld
      case other: String =>
        log.warning(s"Could not parse ts: '$other'")
        false
    }
  }
}
