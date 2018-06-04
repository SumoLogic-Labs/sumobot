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
package com.sumologic.sumobot.plugins.tts

import java.io.File

import akka.actor.Props
import com.sumologic.sumobot.core.model.IncomingMessage
import com.sumologic.sumobot.plugins.BotPlugin

import scala.sys.process._
import scala.util.Try

class TextToSpeech extends BotPlugin {

  private val executable: Option[File] = Try(config.getString("executable")).
    toOption.
    map(e => new File(e)).
    filter(_.canExecute)

  override protected def help: String =
    s"""I can say stuff over the loudspeaker.
       |
       |speak "some text" - Will get me to say the text out loud.
     """.stripMargin

  private val SaySomething = matchText("(speak|say) \"(.*)\"")
  private val BadChars = "|\"".toCharArray.toSet

  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {
    case message@IncomingMessage(SaySomething(_, text), true, _, _, _, _) if executable.isDefined =>
      val cleanedText = text.toCharArray.filterNot(BadChars.contains).mkString
      // Deliberately blocking the actor thread here, so only one say action is happening at the same time.
      log.info(s"Speaking: $cleanedText")
      val command = s"${executable.get.getAbsolutePath} " + "\"" + cleanedText + "\""
      command.run().exitValue()
  }
}
