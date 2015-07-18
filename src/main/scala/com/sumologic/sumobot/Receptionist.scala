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
package com.sumologic.sumobot

import akka.actor.{Actor, ActorRef, Props}
import com.sumologic.sumobot.plugins.BotPlugin.{InitializePlugin, PluginAdded}
import slack.models.{ImOpened, Message}
import slack.rtm.SlackRtmClient
import slack.rtm.SlackRtmConnectionActor.SendMessage

import scala.concurrent.ExecutionContext.Implicits.global

// Ideas
// - reminder for maintenance windows, with n minutes remaining.
// - service log entry creation
// - online help

object Receptionist {

  def props(rtmClient: SlackRtmClient, brain: ActorRef): Props =
    Props(classOf[Receptionist], rtmClient, brain)

  case class SendSlackMessage(channelId: String, text: String)

  case class OpenIM(userId: String, doneRecipient: ActorRef, doneMessage: AnyRef)

  case class BotMessage(canonicalText: String,
                        isAtMention: Boolean,
                        isInstantMessage: Boolean,
                        slackMessage: Message) {

    val addressedToUs: Boolean = isAtMention || isInstantMessage

    def originalText: String = slackMessage.text
  }
}

class Receptionist(rtmClient: SlackRtmClient, brain: ActorRef) extends Actor {

  import com.sumologic.sumobot.Receptionist._

  private val slack = rtmClient.actor
  private val blockingClient = rtmClient.apiClient
  private val selfId = rtmClient.state.self.id
  private val selfName = rtmClient.state.self.name
  rtmClient.addEventListener(self)

  private val atMention = """<@(\w+)>:(.*)""".r
  private val atMentionWithoutColon = """<@(\w+)>\s(.*)""".r
  private val simpleNamePrefix = """(\w+)\:?\s(.*)""".r

  private var pendingIMSessionsByUserId = Map[String, (ActorRef, AnyRef)]()

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SendSlackMessage])
    context.system.eventStream.subscribe(self, classOf[PluginAdded])
    context.system.eventStream.subscribe(self, classOf[OpenIM])
  }


  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(self)
  }

  override def receive: Receive = {

    case PluginAdded(plugin, _, _) =>
      plugin ! InitializePlugin(rtmClient.state, brain)

    case SendSlackMessage(channelId, text) =>
      slack ! SendMessage(channelId, text)

    case ImOpened(user, channel) =>
      pendingIMSessionsByUserId.get(user).foreach {
        tpl =>
          tpl._1 ! tpl._2
          pendingIMSessionsByUserId = pendingIMSessionsByUserId - user
      }

    case OpenIM(userId, doneRecipient, doneMessage) =>
      blockingClient.openIm(userId)
      pendingIMSessionsByUserId = pendingIMSessionsByUserId + (userId ->(doneRecipient, doneMessage))

    case message: Message =>
      val msgToBot = translateMessage(message)
      if (message.user != selfId) {
        context.system.eventStream.publish(msgToBot)
      }
  }

  protected def translateMessage(message: Message): BotMessage = {
    val isInstantMessage = rtmClient.state.ims.exists(_.id == message.channel)
    message.text match {
      case atMention(user, text) if user == selfId =>
        BotMessage(text.trim, true, isInstantMessage, message)
      case atMentionWithoutColon(user, text) if user == selfId =>
        BotMessage(text.trim, true, isInstantMessage, message)
      case simpleNamePrefix(name, text) if name.equalsIgnoreCase(selfName) =>
        BotMessage(text.trim, true, isInstantMessage, message)
      case _ =>
        BotMessage(message.text.trim, false, isInstantMessage, message)
    }
  }
}