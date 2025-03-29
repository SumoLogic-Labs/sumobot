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

import akka.actor.ActorSystem
import com.sumologic.sumobot.core.model.IncomingMessage
import com.sumologic.sumobot.test.annotated.BotPluginTestKit
import org.scalatest.BeforeAndAfterEach
import slack.models.{Group, Im, Team, User, Channel => SlackChannel}

class BotPluginUT extends BotPlugin {
  override protected def help: String = "help msg"
  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {
    case message@IncomingMessage(_, _, _, _, _, _, _) =>
      message.respond("Thanks for the message")
  }

}

class BotPluginTest
  extends BotPluginTestKit(ActorSystem("BotPluginTest")) with BeforeAndAfterEach {
}
