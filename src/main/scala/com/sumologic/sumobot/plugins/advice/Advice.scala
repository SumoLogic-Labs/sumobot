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
package com.sumologic.sumobot.plugins.advice

import com.sumologic.sumobot.core.model.{IncomingMessage, OutgoingMessage}
import com.sumologic.sumobot.plugins.BotPlugin
import org.apache.http.HttpResponse
import org.apache.http.util.EntityUtils
import play.api.libs.json.Json

object Advice {
  val AdviceAbout = BotPlugin.matchText("(what should I do about|what do you think about|how do you handle) (.*)")
  val RandomAdvice = BotPlugin.matchText("I need some advice")

  private[advice] def parseResponse(response: HttpResponse): Seq[String] = {
    val json = Json.parse(EntityUtils.toString(response.getEntity))
    (json \\ "advice").map(_.as[String]).toSeq
  }
}

class Advice extends BotPlugin {

  override protected def help: String =
    s"""
       |what should I do about <something>
       |what do you think about <something>
       |how do you handle <something>
       |I need some advice
    """.stripMargin

  import Advice._

  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {
    case msg@IncomingMessage(RandomAdvice(), _, _, _, _, _, _) =>
      msg.httpGet(s"http://api.adviceslip.com/advice")(respondWithAdvice)

    case msg@IncomingMessage(AdviceAbout(_, something), true, _, _, _, _, _) =>
      msg.httpGet(s"http://api.adviceslip.com/advice/search/${urlEncode(something.trim)}")(respondWithAdvice)
  }

  private def respondWithAdvice(msg: IncomingMessage, response: HttpResponse): OutgoingMessage = {
    if (response.getStatusLine.getStatusCode == 200) {
      val choices = parseResponse(response)
      if (choices.nonEmpty) {
        msg.message(chooseRandom(choices: _*))
      } else {
        msg.response("No advice for you, sorry.")
      }
    } else {
      msg.response("No advice for you, sorry.")
    }
  }
}
