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
package com.sumologic.sumobot.plugins.brain

import com.sumologic.sumobot.core.model.IncomingMessage
import com.sumologic.sumobot.plugins.BotPlugin

class BrainSurgery extends BotPlugin {

  override protected def help =
    """Mess with my brain:
      |
      |dump your brain - I'll tell you everything I know.
      |remember: xxx.yyy=zzz - Will make me remember.
      |forget about xxx.yyy - Will make me forget xxx.
    """.stripMargin

  private val brainDump = matchText(".*dump\\s.*brain.*")

  private val remember = matchText("remember[\\s\\:]+([\\.\\w]+)=(\\w+).*")

  private val forget = matchText("forget about ([\\.\\w]+).*")

  override protected def receiveIncomingMessage = {
    case message@IncomingMessage(remember(key, value), true, _, _, _, _) =>
      blockingBrain.store(key.trim, value.trim)
      message.respond(s"Got it, $key is $value")
    case message@IncomingMessage(forget(key), true, _, _, _, _) =>
      blockingBrain.remove(key.trim)
      message.respond(s"$key? I've forgotten all about it.")
    case message@IncomingMessage(brainDump(), true, _, _, _, _) =>
      val map = blockingBrain.listValues()
      if (map.isEmpty) {
        message.say("My brain is empty.")
      } else {
        message.say(map.toSeq.sortBy(_._1).map(tpl => s"${tpl._1}=${tpl._2}").mkString("\n"))
      }
  }
}
