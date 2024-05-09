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

import org.apache.pekko.actor._
import com.sumologic.sumobot.core.Receptionist.{RtmStateRequest, RtmStateResponse}
import com.sumologic.sumobot.core.model._
import com.sumologic.sumobot.plugins.BotPlugin.{InitializePlugin, PluginAdded, PluginRemoved}
import slack.api.{BlockingSlackApiClient, SlackApiClient}
import slack.models.{ImOpened, Message, MessageChanged, ReactionAdded, ReactionItemMessage, User, Attachment => SAttachment}
import slack.rtm.{RtmState, SlackRtmClient}

import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object Receptionist {

  case class RtmStateRequest(sendTo: ActorRef)

  case class RtmStateResponse(rtmState: RtmState)

  def props(rtmClient: SlackRtmClient,
            syncClient: BlockingSlackApiClient,
            asyncClient: SlackApiClient,
            brain: ActorRef): Props =
    Props(new Receptionist(rtmClient, syncClient, asyncClient, brain))
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

  private var pendingIMSessionsByUserId = Map[String, OutgoingMessage]()

  private val pluginRegistry = context.system.actorOf(Props(classOf[PluginRegistry]), "plugin-registry")

  private var slackNameToIdMapping: Map[String, String] = Map()

  override def preStart(): Unit = {
    Seq(classOf[OutgoingMessage],
      classOf[OutgoingMessageWithAttachments],
      classOf[OutgoingImage],
      classOf[OpenIM],
      classOf[RtmStateRequest],
      classOf[ResponseInProgress],
      classOf[NewChannelTopic],
      classOf[SendIMByUserName]
    ).foreach(context.system.eventStream.subscribe(self, _))

    slackNameToIdMapping = fetchUsers().map{u => u.name -> u.id}.toMap
    log.info(s"Loaded ${slackNameToIdMapping.size} users")
  }

  // VisibleForTesting
  protected def fetchUsers(): Seq[User] = {
    // NOTE(mccartney, 2023-01-30): I couldn't get the syncClient to do the same, it failed with timeout(15s)
    Await.result(asyncClient.listUsers(), atMost = Duration(1, TimeUnit.MINUTES))
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

    case OutgoingMessage(channelId, text, threadTs) =>
      log.info(s"sending - $channelId: $text")
      rtmClient.sendMessage(channelId, text, threadTs)

    case OutgoingMessageWithAttachments(channel, text, threadTs, attachments) =>
      log.info(s"sending - ${channel.name}: $text")
      val attachmentsAsSlackModel = Messages.convertToSlackModel(attachments)
      asyncClient.postChatMessage(channel.id, text, attachments = attachmentsAsSlackModel, threadTs = threadTs)

    case OutgoingImage(channelId, imageFile, contentType, title, comment, threadTimestamp) =>
      log.info(s"Sending image (${imageFile.length()} bytes) to $channelId")
      val fut = asyncClient.uploadFile(content = Left(imageFile), filetype = Some(contentType), title = Some(title),
        filename = Some(imageFile.getName), channels = Some(Seq(channelId)), initialComment = comment,
        thread_ts = threadTimestamp)
      fut.onComplete {
        f => log.info(s"Sending image ended with $f")
      }

    case ImOpened(user, channel) =>
      pendingIMSessionsByUserId.get(user).foreach {
        case outgoingMessage: OutgoingMessage =>
          context.system.eventStream.publish(outgoingMessage.copy(channelId = channel))
          pendingIMSessionsByUserId = pendingIMSessionsByUserId - user
      }

    case OpenIM(userId, doneMessage) =>
      asyncClient.openIm(userId)
      pendingIMSessionsByUserId = pendingIMSessionsByUserId + (userId -> doneMessage)

    case message: Message if !tooOld(message.ts, message) && message.user.isDefined =>
      translateAndDispatch(message.channel, message.user.get, message.text, message.ts, threadTimestamp = message.thread_ts,
        message.attachments.getOrElse(Seq()), fromBot = message.bot_id.isDefined)

    case messageChanged: MessageChanged if !tooOld(messageChanged.ts, messageChanged) && messageChanged.message.user.isDefined =>
      val message = messageChanged.message
      translateAndDispatch(messageChanged.channel, message.user.get, message.text, message.ts,
        attachments = message.attachments.getOrElse(Seq()))

    case RtmStateRequest(sendTo) =>
      sendTo ! RtmStateResponse(rtmClient.state)

    case ResponseInProgress(channelId) =>
      rtmClient.indicateTyping(channelId)

    case ReactionAdded(reaction, ReactionItemMessage(channel, ts), _, user, _) =>
      context.system.eventStream.publish(Reaction(reaction, channel, ts, user))

    case NewChannelTopic(channel, topic) =>
      asyncClient.setConversationTopic(channel.id, topic)

    case SendIMByUserName(userName: String, msg: OutgoingMessage) =>
      this.slackNameToIdMapping.get(userName) match {
        case Some(id) =>
          self ! OpenIM(id, msg)

        case None =>
          log.warning(s"Failed to send IM to user name '$userName' as it doesn't seem to exist")
      }

  }

  protected def translateMessage(channelId: String,
                                 idTimestamp: String,
                                 threadTimestamp: Option[String] = None,
                                 text: String,
                                 attachments: Seq[IncomingMessageAttachment],
                                 from: Sender): IncomingMessage = {
    text match {
      case atMention(user, text) if user == selfId =>
        IncomingMessage(text.trim, true, channelId, idTimestamp, threadTimestamp, attachments, from)
      case atMentionWithoutColon(user, text) if user == selfId =>
        IncomingMessage(text.trim, true, channelId, idTimestamp, threadTimestamp, attachments, from)
      case simpleNamePrefix(name, text) if name.equalsIgnoreCase(selfName) =>
        IncomingMessage(text.trim, true, channelId, idTimestamp, threadTimestamp, attachments, from)
      case _ =>
        val isADirectMessageChannel = channelId.startsWith("D")
        IncomingMessage(text.trim, addressedToUs = isADirectMessageChannel, channelId, idTimestamp, threadTimestamp, attachments, from)
    }
  }

  private def translateAndDispatch(channelId: String,
                                   userId: String,
                                   text: String,
                                   idTimestamp: String,
                                   threadTimestamp: Option[String] = None,
                                   attachments: Seq[SAttachment] = Seq(),
                                   fromBot: Boolean = false): Unit = {
    val sentBy: Sender = if (!fromBot) {
      val slackUser = try {
        syncClient.getUserInfo(userId)
      } catch {
        case e: Exception =>
          throw new IllegalStateException(s"Failed to look up user with id: $userId", e)
      }
      UserSender(slackUser)
    } else {
      BotSender(userId)
    }
    val msgToBot = translateMessage(channelId, idTimestamp, threadTimestamp, text,
      attachments.map(a => IncomingMessageAttachment(a.text.getOrElse(""), a.title.getOrElse(""))),
      sentBy)
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
        val oldestAllowableMessageTime = (System.currentTimeMillis() - messageAgeLimitMillis) / 1000
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
