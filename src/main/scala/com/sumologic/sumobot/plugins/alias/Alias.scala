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
package com.sumologic.sumobot.plugins.alias

import com.sumologic.sumobot.core.model.IncomingMessage
import com.sumologic.sumobot.plugins.BotPlugin

class Alias extends BotPlugin {

  private val CreateAlias = matchText("alias '(.*)' to '(.*)'")
  private val RemoveAlias = matchText("remove alias '(.*)'")
  private val ShowAliases = matchText("show known aliases")
  private val BrainPrefix = "alias."

  override protected def help: String =
    """
      |Create aliases for commands.
      |
      |alias 'something' to 'something else' - Make me run 'something else' when you ask me to do something.
      |remove alias 'something' - Removes the alias.
      |show known aliases - Lists all the aliases.
    """.stripMargin

  private var knownAliases = Map[String, String]()

  override protected def initialize(): Unit = {
    knownAliases = blockingBrain.listValues(BrainPrefix).map(tpl => tpl._1.substring(BrainPrefix.length) -> tpl._2)
  }

  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {
    case message@IncomingMessage(CreateAlias(from, to), _, _, _, _, _) =>
      if (from.isEmpty || to.isEmpty) {
        message.respond("You've gotta give me both parts!")
      } else if (from == to) {
        message.respond("You'll want to alias something to something else!")
      } else {
        knownAliases += (from -> to)
        blockingBrain.store(s"$BrainPrefix$from", to)
        message.respond(s"Ok, remembered that '$from' is '$to'")
      }

    case message@IncomingMessage(text, _, _, _, _, _) if knownAliases.exists(_._1.equalsIgnoreCase(text)) =>
      knownAliases.find(_._1.equalsIgnoreCase(text)).foreach {
        tpl =>
          val replacement = tpl._2
          context.system.eventStream.publish(message.copy(canonicalText = replacement))
      }

    case message@IncomingMessage(RemoveAlias(alias), _, _, _, _, _) =>
      knownAliases.find(_._1.equalsIgnoreCase(alias)) match {
        case Some(knownAlias) =>
          knownAliases -= knownAlias._1
          blockingBrain.remove(knownAlias._1)
          message.respond(s"Alright, forgotten about '${knownAlias._1}'")
        case None =>
          message.respond(s"I don't know anything about $alias")
      }

    case message@IncomingMessage(ShowAliases(alias), _, _, _, _, _) =>
      if (knownAliases.nonEmpty) {
        message.respond(knownAliases.toSeq.sortBy(_._1).map(tpl => s"'${tpl._1}' is '${tpl._2}'").mkString("\n"))
      } else {
        message.respond("No aliases defined!")
      }
  }
}
