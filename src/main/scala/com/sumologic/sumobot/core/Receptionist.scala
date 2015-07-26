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

import akka.actor._
import com.sumologic.sumobot.core.model._
import com.sumologic.sumobot.plugins.BotPlugin.{InitializePlugin, PluginAdded, PluginRemoved}
import slack.models.{ImOpened, Message}
import slack.rtm.SlackRtmClient
import slack.rtm.SlackRtmConnectionActor.SendMessage

import scala.concurrent.ExecutionContext.Implicits.global

object Receptionist {
  def props(rtmClient: SlackRtmClient, brain: ActorRef): Props =
    Props(classOf[Receptionist], rtmClient, brain)
}

class Receptionist(rtmClient: SlackRtmClient, brain: ActorRef) extends Actor {

  private val slack = rtmClient.actor
  private val blockingClient = rtmClient.apiClient
  private val selfId = rtmClient.state.self.id
  private val selfName = rtmClient.state.self.name
  rtmClient.addEventListener(self)

  private val atMention = """<@(\w+)>:(.*)""".r
  private val atMentionWithoutColon = """<@(\w+)>\s(.*)""".r
  private val simpleNamePrefix = """(\w+)\:?\s(.*)""".r

  private var pendingIMSessionsByUserId = Map[String, (ActorRef, AnyRef)]()

  private val pluginRegistry = context.system.actorOf(Props(classOf[PluginRegistry]), "plugin-registry")

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[OutgoingMessage])
    context.system.eventStream.subscribe(self, classOf[OpenIM])
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
      slack ! SendMessage(channel.id, text)

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

  protected def translateMessage(slackMessage: Message): IncomingMessage = {

    val channel = Channel.forMessage(rtmClient.state, slackMessage)
    val sentByUser = rtmClient.state.users.find(_.id == slackMessage.user).
      getOrElse(throw new IllegalStateException(s"Message from unknown user: ${slackMessage.user}"))

    slackMessage.text match {
      case atMention(user, text) if user == selfId =>
        IncomingMessage(text.trim, true, channel, sentByUser)
      case atMentionWithoutColon(user, text) if user == selfId =>
        IncomingMessage(text.trim, true, channel, sentByUser)
      case simpleNamePrefix(name, text) if name.equalsIgnoreCase(selfName) =>
        IncomingMessage(text.trim, true, channel, sentByUser)
      case _ =>
        IncomingMessage(slackMessage.text.trim, channel.isInstanceOf[InstantMessageChannel], channel, sentByUser)
    }
  }
}
