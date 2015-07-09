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
import com.sumologic.sumobot.plugins.aws.AWSCredentialSource
import com.sumologic.sumobot.plugins.awssupport.AWSSupport
import com.sumologic.sumobot.plugins.beer.Beer
import com.sumologic.sumobot.plugins.conversations.Conversations
import com.sumologic.sumobot.plugins.help.Help
import com.sumologic.sumobot.plugins.help.Help.PluginAdded
import com.sumologic.sumobot.plugins.jenkins.{Jenkins, JenkinsJobClient}
import com.sumologic.sumobot.plugins.jira.{Jira, JiraClient}
import com.sumologic.sumobot.plugins.pagerduty.{PagerDuty, PagerDutySchedulesManager}
import com.sumologic.sumobot.plugins.upgradetests.UpgradeTestRunner
import slack.models.Message
import slack.rtm.SlackRtmConnectionActor.SendMessage
import slack.rtm.{RtmState, SlackRtmClient}

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

    def senderName(implicit state: RtmState): Option[String] =
      state.users.find(_.id == slackMessage.user).map(_.name)
  }

  case class AddPlugin(plugin: ActorRef)
}

class Bender(rtmClient: SlackRtmClient) extends Actor {

  import com.sumologic.sumobot.Bender._

  private val slack = rtmClient.actor
  private val blockingClient = rtmClient.apiClient
  private val selfId = rtmClient.state.self.id
  private val selfName = rtmClient.state.self.name
  rtmClient.addEventListener(self)

  private val helpPlugin = context.actorOf(Props(classOf[Help]), "help")

  private var plugins: Seq[ActorRef] = Nil

  self ! AddPlugin(helpPlugin)

  JenkinsJobClient.createClient("jenkins").foreach {
    jenkinsClient =>
      self ! AddPlugin(context.actorOf(props = Jenkins.props(rtmClient.state, "jenkins", jenkinsClient)))
  }

  JenkinsJobClient.createClient("hudson").foreach {
    hudsonClient =>
      self ! AddPlugin(context.actorOf(props = Jenkins.props(rtmClient.state, "hudson", hudsonClient)))
      self ! AddPlugin(context.actorOf(Props(classOf[UpgradeTestRunner], rtmClient.state, hudsonClient), "upgrade-test-runner"))
  }

  PagerDutySchedulesManager.createClient().foreach {
    pagerDutySchedulesManager =>
      self ! AddPlugin(context.actorOf(Props(classOf[PagerDuty], pagerDutySchedulesManager), "pagerduty"))
  }

  JiraClient.createClient.foreach {
    jiraClient =>
      self ! AddPlugin(context.actorOf(Props(classOf[Jira], jiraClient), "jira"))
  }

  self ! AddPlugin(context.actorOf(Props(classOf[Conversations], rtmClient.state), "conversations"))
  self ! AddPlugin(context.actorOf(Props(classOf[Beer]), "beer"))

  val awsCreds = AWSCredentialSource.credentials
  if (awsCreds.nonEmpty) {
    self ! AddPlugin(context.actorOf(Props(classOf[AWSSupport], awsCreds), "aws-support"))
  }

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
      helpPlugin ! PluginAdded(plugin)

    case message: Message =>
      val msgToBot = translateMessage(message)
      if (message.user != selfId) {
        plugins.foreach {
          plugin =>
            plugin ! msgToBot
        }
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
