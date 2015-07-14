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
import akka.pattern.pipe
import com.sumologic.sumobot.Receptionist.{BotMessage, SendSlackMessage}
import com.sumologic.sumobot.plugins.BotPlugin.{PluginAdded, PluginRemoved}

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.matching.Regex

object BotPlugin {
  case object RequestHelp

  case class PluginAdded(plugin: ActorRef, name: String, help: String)
  
  case class PluginRemoved(plugin: ActorRef, name: String)
  
  def matchText(regex: String): Regex = ("(?i)" + regex).r
}

trait BotPlugin
  extends Actor
  with ActorLogging
  with Emotions {

  type ReceiveText = PartialFunction[String, Unit]

  // For plugins to implement.

  protected def receiveText: ReceiveText

  protected def name: String

  protected def help: String

  // Helpers for plugins to use.

  protected var botMessage: BotMessage = _

  protected def matchText(regex: String): Regex = BotPlugin.matchText(regex)

  protected def respondInFuture(body: BotMessage => SendSlackMessage)(implicit executor : scala.concurrent.ExecutionContext): Unit = {
    val msg = botMessage
    Future {
      try {
        body(msg)
      } catch {
        case NonFatal(e) =>
          log.error(e, "Execution failed.")
          msg.response("Execution failed.")
      }
    } foreach(context.system.eventStream.publish)
  }

  // Implementation. Most plugins should not override.

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[BotMessage])
    context.system.eventStream.publish(PluginAdded(self, name, help))
  }

  
  override def postStop(): Unit = {
    context.system.eventStream.publish(PluginRemoved(self, name))
    context.system.eventStream.unsubscribe(self)
  }

  override def receive: Receive = receiveBotMessage

  private final def receiveTextInternal: ReceiveText = receiveText orElse {
    case _ =>
  }

  protected final def receiveBotMessage: Receive = {
    case botMessage @ BotMessage(text, _, _, _, _) =>
      try {
        this.botMessage = botMessage
        receiveTextInternal(text)
      } finally {
        this.botMessage = null
      }
  }
}

