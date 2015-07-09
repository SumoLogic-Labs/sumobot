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

import akka.actor.{Actor, ActorContext, ActorRef, Props}
import com.sumologic.sumobot.plugins.conversations.Conversations
import com.sumologic.sumobot.plugins.jenkins.{JenkinsJobClient, Jenkins}
import com.sumologic.sumobot.plugins.upgradetests.UpgradeTestRunner
import slack.models.Message
import slack.rtm.{RtmState, SlackRtmClient, SlackRtmConnectionActor}

import scala.concurrent.ExecutionContext.Implicits.global

// Ideas
// - reminder for maintenance windows, with n minutes remaining.
// - service log entry creation
// - online help

object Bender {

  def props(rtmClient: SlackRtmClient): Props = Props(classOf[Bender], rtmClient)

  case class SendSlackMessage(channelId: String, text: String)

  case class OpenIM(userId: String)

  case class BotMessage(canonicalText: String,
                        isAtMention: Boolean,
                        isInstantMessage: Boolean,
                        slackMessage: Message) {
    val addressedToUs: Boolean = isAtMention || isInstantMessage

    def response(text: String) = SendSlackMessage(slackMessage.channel, responsePrefix + text)

    def message(text: String) = SendSlackMessage(slackMessage.channel, text)

    def say(text: String)(implicit context: ActorContext) = context.sender() ! message(text)

    def respond(text: String)(implicit context: ActorContext) = context.sender() ! response(text)

    def responsePrefix: String = if (isInstantMessage) "" else s"<@${slackMessage.user}>: "

    def originalText: String = slackMessage.text

    def channelName(implicit state: RtmState): Option[String] =
      state.channels.find(_.id == slackMessage.channel).map(_.name)
  }

  case class AddPlugin(plugin: ActorRef)
}

class Bender(rtmClient: SlackRtmClient) extends Actor {

  import Bender._
  import SlackRtmConnectionActor._

  private val slack = rtmClient.actor
  private val blockingClient = rtmClient.apiClient
  private val selfId = rtmClient.state.self.id
  private val selfName = rtmClient.state.self.name
  rtmClient.addEventListener(self)

  private val jenkinsJobClient: Option[JenkinsJobClient] = JenkinsJobClient.createClient("jenkins")
  private val hudsonJobClient: Option[JenkinsJobClient] = JenkinsJobClient.createClient("hudson")

  private val jenkinsPlugins: Seq[ActorRef] = List(jenkinsJobClient, hudsonJobClient).flatten.
    map(client => context.actorOf(props = Jenkins.props(rtmClient.state, client.name, client), name = client.name))

  private var plugins: Seq[ActorRef] = jenkinsPlugins ++ (
    context.actorOf(Props(classOf[Conversations], rtmClient.state), "conversations") ::
      context.actorOf(Props(classOf[UpgradeTestRunner], rtmClient.state), "upgrade-test-runner") ::
      Nil)

  private val atMention = """<@(\w+)>:(.*)""".r
  private val atMentionWithoutColon = """<@(\w+)>\s(.*)""".r
  private val simpleNamePrefix = """(\w+)\:?\s(.*)""".r

  override def receive: Receive = {
    case SendSlackMessage(channelId, text) =>
      slack ! SendMessage(channelId, text)

    case OpenIM(userId) =>
      blockingClient.openIm(userId)

    case AddPlugin(plugin) =>
      plugins = plugins :+ plugin

    case message: Message =>
      val msgToBot = translateMessage(message)
      plugins.foreach {
        plugin =>
          plugin ! msgToBot
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
