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
import com.sumologic.sumobot.Bootstrap
import com.sumologic.sumobot.brain.BlockingBrain
import com.sumologic.sumobot.core.{IncomingMessage, InstantMessageChannel, OutgoingMessage}
import com.sumologic.sumobot.plugins.BotPlugin.{InitializePlugin, PluginAdded, PluginRemoved}
import slack.rtm.RtmState

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import scala.util.matching.Regex

object BotPlugin {

  case object RequestHelp

  case class PluginAdded(plugin: ActorRef, help: String)

  case class PluginRemoved(plugin: ActorRef)

  case class InitializePlugin(state: RtmState, brain: ActorRef, pluginRegistry: ActorRef)

  def matchText(regex: String): Regex = ("(?i)" + regex).r
}

abstract class BotPlugin
    extends Actor
    with ActorLogging
    with Emotions {

  type ReceiveIncomingMessage = PartialFunction[IncomingMessage, Unit]

  protected var state: RtmState = _

  protected var brain: ActorRef = _

  protected var pluginRegistry: ActorRef = _

  // For plugins to implement.

  protected def receiveIncomingMessage: ReceiveIncomingMessage

  protected def help: String

  class RichIncomingMessage(msg: IncomingMessage) {
    // Helpers for plugins to use.
    def response(text: String) = OutgoingMessage(msg.channel, responsePrefix + text)

    def message(text: String) = OutgoingMessage(msg.channel, text)

    def say(text: String) = context.system.eventStream.publish(message(text))

    def respond(text: String) = context.system.eventStream.publish(response(text))

    private def responsePrefix: String = if (msg.channel.isInstanceOf[InstantMessageChannel]) "" else s"<@${msg.sentByUser.id}>: "

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

    def respondInFuture(body: IncomingMessage => OutgoingMessage)(implicit executor: scala.concurrent.ExecutionContext): Unit = {
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

  implicit def enrichIncomingMessage(msg: IncomingMessage): RichIncomingMessage = new RichIncomingMessage(msg)

  protected def matchText(regex: String): Regex = BotPlugin.matchText(regex)

  protected def blockingBrain: BlockingBrain = new BlockingBrain(brain)

  // Implementation. Most plugins should not override.

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[IncomingMessage])
    Bootstrap.receptionist.foreach(_ ! PluginAdded(self, help))
  }

  override def postStop(): Unit = {
    Bootstrap.receptionist.foreach(_ ! PluginRemoved(self))
    context.system.eventStream.unsubscribe(self)
  }

  private final def receiveIncomingMessageInternal: ReceiveIncomingMessage = receiveIncomingMessage orElse {
    case ignore =>
  }

  override def receive: Receive = uninitialized orElse pluginReceive

  private def uninitialized: Receive = {
    case InitializePlugin(newState, newBrain, newPluginRegistry) =>
      this.state = newState
      this.brain = newBrain
      this.pluginRegistry = newPluginRegistry
      context.become(initialized orElse pluginReceive)
  }

  protected final def initialized: Receive = {
    case message@IncomingMessage(text, _, _, _) =>
      receiveIncomingMessageInternal(message)
  }

  protected def pluginReceive: Receive = Map.empty
}

