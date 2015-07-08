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

import akka.actor.{ActorLogging, Actor}
import com.sumologic.sumobot.Bender.BotMessage
import com.sumologic.sumobot.plugins.BotPlugin.{SendHelp, RequestHelp}

import scala.util.matching.Regex

object BotPlugin {
  case object RequestHelp
  case class SendHelp(name: String, helpText: String)
}

trait BotPlugin
  extends Actor
  with Emotions {

  type ReceiveText = PartialFunction[String, Unit]

  // For plugins to implement.

  protected def receiveText: ReceiveText

  protected def name: String

  protected def help: String

  // Helpers for plugins to use.

  protected var botMessage: BotMessage = _

  protected def matchText(regex: String): Regex = ("(?i)" + regex).r

  // Implementation. Most plugins should not override.

  override def receive: Receive = receiveBotMessage orElse receiveHelpRequest

  private def receiveHelpRequest: Receive = {
    case RequestHelp =>
      sender() ! SendHelp(name, help)
  }

  private final def receiveTextInternal: ReceiveText = receiveText orElse {
    case _ =>
  }

  protected final def receiveBotMessage: Receive = {
    case botMessage @ BotMessage(text, _, _, _) =>
      try {
        this.botMessage = botMessage
        receiveTextInternal(text)
      } finally {
        this.botMessage = null
      }
  }
}

