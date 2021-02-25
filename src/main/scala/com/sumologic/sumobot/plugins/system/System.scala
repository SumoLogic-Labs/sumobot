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
package com.sumologic.sumobot.plugins.system

import com.sumologic.sumobot.core.Bootstrap
import com.sumologic.sumobot.core.model.IncomingMessage
import com.sumologic.sumobot.plugins.{BotPlugin, OperatorLimits}

import java.net.InetAddress
import java.util.Date
import scala.concurrent.duration._

class System
  extends BotPlugin
    with OperatorLimits {
  override protected def help =
    """Low-level system stuff:
      |
      |where are you running? - And I'll tell you which host I'm on.
      |when did you start? - I'll tell you when I was started.
      |die on <hostname> - Will cause me to shut down if you're allowed and I'm on that host.
    """.stripMargin

  private val WhereAreYou = matchText("where are you running.*")
  private val WhenDidYouStart = matchText("when did you (start|launch|boot).*")
  private val DieOn = matchText("die on ([a-zA-Z0-9\\.\\-]+)") // Per RFC952.

  private val hostname = InetAddress.getLocalHost.getHostName
  private val hostAddress = InetAddress.getLocalHost.getHostAddress
  private val startTime = new Date().toString

  override protected def receiveIncomingMessage = {
    case message@IncomingMessage(WhereAreYou(), true, _, _, _, _, _) =>
      message.respond(s"I'm running at $hostname ($hostAddress)")
    case message@IncomingMessage(WhenDidYouStart(_), true, _, _, _, _, _) =>
      message.respond(s"I started at $startTime")
    case message@IncomingMessage(DieOn(host), true, _, _, _, _, _) =>

      if (host.trim.equalsIgnoreCase(hostname)) {
        if (!sentByOperator(message)) {
          message.respond(s"Sorry, ${message.sentBy.slackReference}, I can't do that.")
        } else {
          message.respond(s"Sayonara, ${message.sentBy.slackReference}!")
          context.system.scheduler.scheduleOnce(2.seconds, new Runnable {
            override def run() = {
              Bootstrap.shutdown()
            }
          })
        }
      }
  }
}
