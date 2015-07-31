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
package com.sumologic.sumobot.plugins.help

import akka.actor.{ActorSystem, Props}
import com.sumologic.sumobot.core.model.{IncomingMessage, InstantMessageChannel}
import com.sumologic.sumobot.core.PluginRegistry
import com.sumologic.sumobot.plugins.BotPlugin.{InitializePlugin, PluginAdded}
import com.sumologic.sumobot.plugins.conversations.Conversations
import com.sumologic.sumobot.test.BotPluginTestKit
import org.scalatest.{Matchers, WordSpecLike}

class HelpTest extends BotPluginTestKit(ActorSystem("HelpTest")) {

  val helpRef = system.actorOf(Props[Help], "help")

  val reg = system.actorOf(Props(classOf[PluginRegistry]))
  val mock = system.actorOf(Props(classOf[Conversations]), "mock")

  reg ! PluginAdded(mock, "mock help")
  reg ! PluginAdded(helpRef, "help help")

  helpRef ! InitializePlugin(null, null, reg)

  val user = mockUser("123", "jshmoe")

  "help" should {
    "return list of plugins" in {
      helpRef ! IncomingMessage("help", true, InstantMessageChannel("125", user), user)
      confirmOutgoingMessage {
        msg =>
          msg.text should be("help\nmock")
      }
    }

    "return help for known plugins" in {
      helpRef ! IncomingMessage("help mock", true, InstantMessageChannel("125", user), user)
      confirmOutgoingMessage {
        msg =>
          msg.text should include("mock help")
      }
    }

    "return an error for unknown commands" in {
      helpRef ! IncomingMessage("help test", true, InstantMessageChannel("125", user), user)
      confirmOutgoingMessage {
        msg =>
          msg.text should include("Sorry, I don't know")
      }
    }

    "work with ? variants" in {
      "?" should fullyMatch regex Help.ListPlugins
      "?" should fullyMatch regex Help.ListPlugins
      "help  " should fullyMatch regex Help.ListPlugins
      "? me " should fullyMatch regex Help.HelpForPlugin
      "help me " should fullyMatch regex Help.HelpForPlugin
    }
  }
}
