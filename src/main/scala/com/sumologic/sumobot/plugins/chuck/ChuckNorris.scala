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
package com.sumologic.sumobot.plugins.chuck

import java.net.URLEncoder

import com.sumologic.sumobot.core.model.{IncomingMessage, OutgoingMessage}
import com.sumologic.sumobot.plugins.BotPlugin
import org.apache.http.HttpResponse
import org.apache.http.util.EntityUtils
import play.libs.Json
import slack.models.User

class ChuckNorris extends BotPlugin {
  override protected def help: String =
    s"""I can Chuck Norris someone.
       |
       |chuck norris - Generally Chuck Norris.
       |chuck norris me - Chuck Norris yourself.
       |chuck norris <person> - Chuck Norris them.
     """.stripMargin

  private val ChuckNorrisAtMention = matchText(s"chuck norris $UserId.*")
  private val ChuckNorrisMe = matchText(s"chuck norris me")
  private val ChuckNorris = matchText(s"chuck norris")
  private val BaseUrl = "http://api.icndb.com/jokes/random"

  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {
    case msg@IncomingMessage(ChuckNorris(), _, _, _) =>
      msg.httpGet(BaseUrl)(convertResponse)

    case msg@IncomingMessage(ChuckNorrisMe(), _, _, sentByUser) =>
      msg.httpGet(url(sentByUser))(convertResponse)

    case msg@IncomingMessage(ChuckNorrisAtMention(userId), _, _, _) =>
      userById(userId).foreach {
        user =>
          msg.httpGet(url(user))(convertResponse)
      }
  }

  private def convertResponse(message: IncomingMessage, response: HttpResponse): OutgoingMessage = {
    if (response.getStatusLine.getStatusCode == 200) {
      val json = Json.parse(EntityUtils.toString(response.getEntity))
      val joke = json.at("/value/joke")
      val cleanJoke = joke.asText().
        replaceAllLiterally("&quot;", "\"")
      message.message(cleanJoke)
    } else {
      message.message("Chuck Norris is busy right now.")
    }
  }

  private def url(user: User): String =
    user.profile match {
      case Some(profile) =>
        s"$BaseUrl?firstName=${URLEncoder.encode(profile.first_name.getOrElse(user.name), "utf-8")}&lastName=${URLEncoder.encode(profile.last_name.getOrElse(""), "utf-8")}"
      case None =>
        s"$BaseUrl?firstName=${URLEncoder.encode(user.name, "utf-8")}&lastName="
    }
}
