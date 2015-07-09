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
import com.sumologic.sumobot.Bender
import com.sumologic.sumobot.Bender.SendSlackMessage
import com.sumologic.sumobot.plugins.BotPlugin
import slack.rtm.RtmState

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class Conversations(state: RtmState) extends BotPlugin with ActorLogging {

  override protected def name: String = "conversations"

  override protected def help: String =
    s"""
       |A few pointless, but fun interactions with bender, for example:
       |
       |count to <n> - Causes ${state.self.name} to count to the given number.
       |tell @user to <something> - Causes ${state.self.name} to send an instant message with the given text to the given user.
    """.stripMargin

  private val CountToN = matchText("count to (\\d+).*")
  private val CountDownFromN = matchText("count down from (\\d+).*")
  private val TellColon = matchText("tell <@(\\w+)>[:]?\\s(.*)")
  private val TellTo = matchText("tell <@(\\w+)> to (.*)")
  private val TellHe = matchText("tell <@(\\w+)> he (.*)")
  private val TellShe = matchText("tell <@(\\w+)> she (.*)")
  private val SayInChannel = matchText("say in <#(C\\w+)>:(.*)")
  private val FuckOff = matchText("fuck off.*")
  private val Sup = matchText("sup (\\S+).*")
  private val SupAtMention = matchText("sup <@(\\w+)>.*")
  private val FuckYou = matchText("fuck you.*")

  private val NumberStrings =
    Array("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

  override protected def receiveText: ReceiveText = {
    case "sup" if botMessage.addressedToUs =>
      context.system.scheduler.scheduleOnce(1.seconds, sender(), botMessage.response(s"What's up homie! $cheerful"))

    case CountToN(number) if botMessage.addressedToUs =>
      if (number.toInt > NumberStrings.length - 1) {
        botMessage.respond(s"I can only count to ${NumberStrings.length - 1}!")
      } else {
        (1 to number.toInt).map(i => i -> NumberStrings(i)).foreach {
          tuple =>
            context.system.scheduler.scheduleOnce(tuple._1.seconds, sender(), botMessage.message(s"${tuple._2}!"))
        }
      }

    case CountDownFromN(number) if botMessage.addressedToUs =>
      val start = number.toInt + 1
      if (start > NumberStrings.length) {
        botMessage.respond(s"I can only count down from ${NumberStrings.length - 1}!")
      } else {
        (1 to start).map(i => i -> NumberStrings(start - i)).foreach {
          tuple =>
            context.system.scheduler.scheduleOnce(tuple._1.seconds, sender(), botMessage.message(s"${tuple._2}!"))
        }
      }

    case TellColon(recipientUserId, what) if botMessage.addressedToUs =>
      tell(recipientUserId, what)

    case TellTo(recipientUserId, what) if botMessage.addressedToUs =>
      tell(recipientUserId, what)

    case TellHe(recipientUserId, what) if botMessage.addressedToUs =>
      tell(recipientUserId, "you " + what)

    case TellShe(recipientUserId, what) if botMessage.addressedToUs =>
      tell(recipientUserId, "you " + what)

    case Sup(name) if name == state.self.name =>
      botMessage.respond("What is up!!")

    case SupAtMention(userId) if userId == state.self.id =>
      botMessage.say(s"What is up, <@${botMessage.slackMessage.user}>.")

    case SayInChannel(channelId, what) if botMessage.addressedToUs =>
      sender() ! SendSlackMessage(channelId, what)
      botMessage.respond(s"Message sent.")

    case FuckOff() =>
      botMessage.respond("Same to you.")

    case FuckYou() =>
      botMessage.respond("This is the worst kind of discrimination there is: the kind against me!")
  }

  private def tell(recipientUserId: String, what: String): Unit = {
    if (recipientUserId == state.self.id) {
      botMessage.respond(s"Dude. I can't talk to myself. $puzzled")
    } else {
      state.getUserById(recipientUserId) match {
        case Some(user) =>
          state.ims.find(_.user == user.id) match {
            case Some(im) =>
              sender() ! Bender.SendSlackMessage(im.id, what)
              botMessage.respond(s"Message sent.")
            case None =>
              sender() ! Bender.OpenIM(recipientUserId)
              log.info(s"Opening IM channel to ${user.name}")
              val newMessage = botMessage.copy()
              context.system.scheduler.scheduleOnce(1.seconds, self, newMessage)
          }
        case None =>
          botMessage.respond(s"I don't know who that is. $puzzled")
      }
    }
  }
}
