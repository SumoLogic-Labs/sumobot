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
package com.sumologic.sumobot.plugins

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.sumologic.sumobot.Receptionist.{BotMessage, SendSlackMessage}
import com.sumologic.sumobot.plugins.BotPlugin.{InitializePlugin, PluginAdded, PluginRemoved}
import slack.rtm.RtmState

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import scala.util.matching.Regex

object BotPlugin {

  case object RequestHelp

  case class PluginAdded(plugin: ActorRef, name: String, help: String)

  case class PluginRemoved(plugin: ActorRef, name: String)

  case class InitializePlugin(state: RtmState, brain: ActorRef)

  def matchText(regex: String): Regex = ("(?i)" + regex).r
}


abstract class BotPlugin
  extends Actor
  with ActorLogging
  with Emotions {

  type ReceiveBotMessage = PartialFunction[BotMessage, Unit]

  protected var state: RtmState = _

  protected var brain: ActorRef = _

  // For plugins to implement.

  protected def receiveBotMessage: ReceiveBotMessage

  protected def name: String

  protected def help: String


  class RichIncomingMessage(msg: BotMessage) {
    // Helpers for plugins to use.
    def response(text: String) = SendSlackMessage(msg.slackMessage.channel, responsePrefix + text)

    def message(text: String) = SendSlackMessage(msg.slackMessage.channel, text)

    def say(text: String) = context.system.eventStream.publish(message(text))

    def respond(text: String) = context.system.eventStream.publish(response(text))

    def responsePrefix: String = if (msg.isInstantMessage) "" else s"<@${msg.slackMessage.user}>: "

    def username(id: String): Option[String] =
      state.users.find(_.id == id).map(_.name)

    def channelName: Option[String] =
      state.channels.find(_.id == msg.slackMessage.channel).map(_.name)

    def imName: Option[String] =
      state.ims.find(_.id == msg.slackMessage.channel).map(_.user).flatMap(username)

    def senderName: Option[String] =
      state.users.find(_.id == msg.slackMessage.user).map(_.name)

    def scheduleResponse(delay: FiniteDuration, text: String): Unit = {
      context.system.scheduler.scheduleOnce(delay, new Runnable() {
        override def run(): Unit = context.system.eventStream.publish(response(text))
      })
    }

    def scheduleMessage(delay: FiniteDuration, text: String): Unit = {
      context.system.scheduler.scheduleOnce(delay, new Runnable() {
        override def run(): Unit = context.system.eventStream.publish(message(text))
      })
    }

    def respondInFuture(body: BotMessage => SendSlackMessage)(implicit executor: scala.concurrent.ExecutionContext): Unit = {
      Future {
        try {
          body(msg)
        } catch {
          case NonFatal(e) =>
            log.error(e, "Execution failed.")
            msg.response("Execution failed.")
        }
      } foreach context.system.eventStream.publish
    }
  }

  implicit def wrapMessage(msg: BotMessage): RichIncomingMessage = new RichIncomingMessage(msg)

  protected def matchText(regex: String): Regex = BotPlugin.matchText(regex)

  // Implementation. Most plugins should not override.

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[BotMessage])
    context.system.eventStream.publish(PluginAdded(self, name, help))
  }


  override def postStop(): Unit = {
    context.system.eventStream.publish(PluginRemoved(self, name))
    context.system.eventStream.unsubscribe(self)
  }

  private final def receiveBotMessageInternal: ReceiveBotMessage = receiveBotMessage orElse {
    case _ =>
  }

  override def receive: Receive = uninitialized orElse pluginReceive

  private def uninitialized: Receive = {
    case InitializePlugin(newState, newBrain) =>
      this.state = newState
      this.brain = newBrain
      context.become(initialized orElse pluginReceive)
  }

  protected final def initialized: Receive = {
    case botMessage@BotMessage(text, _, _, _) =>
      receiveBotMessageInternal(botMessage)
  }

  protected def pluginReceive: Receive = Map.empty
}

