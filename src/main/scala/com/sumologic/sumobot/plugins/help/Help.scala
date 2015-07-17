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
import com.sumologic.sumobot.Receptionist.BotMessage
import com.sumologic.sumobot.plugins.BotPlugin
import com.sumologic.sumobot.plugins.BotPlugin.PluginAdded


class Help extends BotPlugin with ActorLogging {
  override protected def name = "help"

  override protected def help =
    s"""I can help you understand plugins.
       |
       |help - I'll tell you what plugins I've got.
       |help <plugin>. - I'll tell you how <plugin> works.
     """.stripMargin

  private var helpText = Map[String, String]().empty

  override def preStart(): Unit = {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[PluginAdded])
  }

  override def postStop(): Unit = {
    super.postStop()
    context.system.eventStream.unsubscribe(self)
  }

  override protected def pluginReceive: Receive = {
    case PluginAdded(_, pluginName, text) =>
      log.info(s"Adding help for plugin '$pluginName'")
      helpText = helpText + (pluginName -> text)
  }

  private val ListPlugins = matchText("help")
  private val HelpForPlugin = matchText("help ([\\-\\w]+).*")

  override protected def receiveBotMessage = {
    case botMessage@BotMessage(ListPlugins(), _, _, _) if botMessage.addressedToUs =>
      botMessage.say(helpText.keys.toList.sorted.mkString("\n"))

    case botMessage@BotMessage(HelpForPlugin(pluginName), _, _, _) if botMessage.addressedToUs =>
      helpText.get(pluginName) match {
        case Some(text) =>
          botMessage.say(text)
        case None =>
          botMessage.respond(s"Sorry, I don't know $pluginName")
      }
  }
}
