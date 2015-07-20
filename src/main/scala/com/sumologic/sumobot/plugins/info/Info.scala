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
package com.sumologic.sumobot.plugins.info

import java.net.InetAddress
import java.util.Date

import com.sumologic.sumobot.core.IncomingMessage
import com.sumologic.sumobot.plugins.BotPlugin

class Info extends BotPlugin {
  override protected def help =
    """You can ask me about myself:
      |
      |where are you running? - And I'll tell you which host I'm on.
      |when did you start? - I'll tell you when I was started.
    """.stripMargin

  private val whereAreYou = matchText("where are you running.*")
  private val whenDidYouStart = matchText("when did you (start|launch|boot).*")

  private val hostname = InetAddress.getLocalHost.getHostName
  private val hostAddress = InetAddress.getLocalHost.getHostAddress
  private val startTime = new Date().toString

  override protected def receiveIncomingMessage = {
    case message@IncomingMessage(whereAreYou(), true, _, _) =>
      message.respond(s"I'm running at $hostname ($hostAddress)")
    case message@IncomingMessage(whenDidYouStart(_), true, _, _) =>
      message.respond(s"I started at $startTime")
  }
}
