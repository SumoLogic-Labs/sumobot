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

import akka.actor.ActorLogging
import akka.pattern.ask
import akka.util.Timeout
import com.sumologic.sumobot.core.IncomingMessage
import com.sumologic.sumobot.core.PluginRegistry.{RequestPluginList, PluginList}
import com.sumologic.sumobot.plugins.BotPlugin
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class Help extends BotPlugin with ActorLogging {
  override protected def help =
    s"""I can help you understand plugins.
       |
       |help - I'll tell you what plugins I've got.
       |help <plugin>. - I'll tell you how <plugin> works.
     """.stripMargin

  private val ListPlugins = matchText("help")
  private val HelpForPlugin = matchText("help ([\\-\\w]+).*")

  override protected def receiveIncomingMessage = {
    case message@IncomingMessage(ListPlugins(), true, _) =>
      val msg = message
      implicit val timeout = Timeout(5.seconds)
      pluginRegistry ? RequestPluginList onSuccess {
        case PluginList(plugins) =>
          msg.respond(plugins.map(_.name).sorted.mkString("\n"))
      }

    case message@IncomingMessage(HelpForPlugin(pluginName), true, _) =>
      val msg = message
      implicit val timeout = Timeout(5.seconds)
      pluginRegistry ? RequestPluginList onSuccess {
        case PluginList(plugins) =>
          plugins.find(_.name.equalsIgnoreCase(pluginName)) match {
            case Some(plugin) =>
              msg.say(plugin.help)
            case None =>
              msg.respond(s"Sorry, I don't know $pluginName")
          }
      }
  }
}
