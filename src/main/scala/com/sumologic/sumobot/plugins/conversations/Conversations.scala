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
package com.sumologic.sumobot.plugins.conversations

import akka.actor.ActorLogging
import com.sumologic.sumobot.Receptionist
import com.sumologic.sumobot.Receptionist.{BotMessage, OpenIM, SendSlackMessage}
import com.sumologic.sumobot.plugins.BotPlugin

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class Conversations extends BotPlugin with ActorLogging {

  override protected def help: String =
    s"""
       |You can tell me to some random stuff for you:
       |
       |count to <n> - I'll count to n.
       |count down from <n> - I'll count down from n.
       |say in <channel>: <message> - I'll say what you asked me in <channel>.
       |tell @user to <message> - I'll tell <user> what you asked me to tell them via IM.
    """.stripMargin

  private val CountToN = matchText("count to (\\d+).*")
  private val CountDownFromN = matchText("count down from (\\d+).*")
  private val TellColon = matchText("tell <@(\\w+)>[:]?\\s(.*)")
  private val TellTo = matchText("tell <@(\\w+)> to (.*)")
  private val TellHe = matchText("tell <@(\\w+)> he (.*)")
  private val TellShe = matchText("tell <@(\\w+)> she (.*)")
  private val SayInChannel = matchText("say in <#(C\\w+)>[:]?(.*)")
  private val FuckOff = matchText("fuck off.*")
  private val Sup = matchText("sup (\\S+).*")
  private val SupAtMention = matchText("sup <@(\\w+)>.*")
  private val FuckYou = matchText("fuck you.*")

  private val NumberStrings =
    Array("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

  override protected def receiveBotMessage: ReceiveBotMessage = {
    case botMessage @ BotMessage("sup", _, _, _) if botMessage.addressedToUs =>
      botMessage.scheduleResponse(1.seconds, s"What's up homie! $cheerful")

    case botMessage @ BotMessage(CountToN(number), _, _, _) if botMessage.addressedToUs =>
      if (number.toInt > NumberStrings.length - 1) {
        botMessage.respond(s"I can only count to ${NumberStrings.length - 1}!")
      } else {
        (1 to number.toInt).map(i => i -> NumberStrings(i)).foreach {
          tuple =>
            botMessage.scheduleMessage(tuple._1.seconds, s"${tuple._2}!")
        }
      }

    case botMessage @ BotMessage(CountDownFromN(number), _, _, _) if botMessage.addressedToUs =>
      val start = number.toInt + 1
      if (start > NumberStrings.length) {
        botMessage.respond(s"I can only count down from ${NumberStrings.length - 1}!")
      } else {
        (1 to start).map(i => i -> NumberStrings(start - i)).foreach {
          tuple =>
            botMessage.scheduleMessage(tuple._1.seconds, s"${tuple._2}!")
        }
      }

    case botMessage @ BotMessage(TellColon(recipientUserId, what), _, _, _) if botMessage.addressedToUs =>
      tell(botMessage, recipientUserId, what)

    case botMessage @ BotMessage(TellTo(recipientUserId, what), _, _, _) if botMessage.addressedToUs =>
      tell(botMessage, recipientUserId, what)

    case botMessage @ BotMessage(TellHe(recipientUserId, what), _, _, _) if botMessage.addressedToUs =>
      tell(botMessage, recipientUserId, "you " + what)

    case botMessage @ BotMessage(TellShe(recipientUserId, what), _, _, _) if botMessage.addressedToUs =>
      tell(botMessage, recipientUserId, "you " + what)

    case botMessage @ BotMessage(Sup(name), _, _, _) if name == state.self.name =>
      botMessage.respond("What is up!!")

    case botMessage @ BotMessage(SupAtMention(userId), _, _, _) if userId == state.self.id =>
      botMessage.say(s"What is up, <@${botMessage.slackMessage.user}>.")

    case botMessage @ BotMessage(SayInChannel(channelId, what), _, _, _) if botMessage.addressedToUs =>
      context.system.eventStream.publish(SendSlackMessage(channelId, what))
      botMessage.respond(s"Message sent.")

    case botMessage @ BotMessage(FuckOff(), _, _, _) =>
      botMessage.respond("Same to you.")

    case botMessage @ BotMessage(FuckYou(), _, _, _) =>
      botMessage.respond("This is the worst kind of discrimination there is: the kind against me!")
  }

  private def tell(botMessage: BotMessage, recipientUserId: String, what: String): Unit = {
    if (recipientUserId == state.self.id) {
      botMessage.respond(s"Dude. I can't talk to myself. $puzzled")
    } else {
      state.getUserById(recipientUserId) match {
        case Some(user) =>
          state.ims.find(_.user == user.id) match {
            case Some(im) =>
              context.system.eventStream.publish(SendSlackMessage(im.id, what))
              botMessage.respond(s"Message sent.")
            case None =>
              val newMessage = botMessage.copy()
              context.system.eventStream.publish(OpenIM(recipientUserId, self, newMessage))
              log.info(s"Opening IM channel to ${user.name}")
          }
        case None =>
          botMessage.respond(s"I don't know who that is. $puzzled")
      }
    }
  }
}
