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

import java.text.DateFormat
import java.util.Date

import akka.actor.ActorLogging
import com.sumologic.sumobot.core._
import com.sumologic.sumobot.core.model._
import com.sumologic.sumobot.plugins.BotPlugin

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
  private val TellColon = matchText(s"tell $UserId[:]?\\s(.*)")
  private val TellTo = matchText(s"tell $UserId to (.*)")
  private val TellHe = matchText(s"tell $UserId he (.*)")
  private val TellShe = matchText(s"tell $UserId she (.*)")
  private val SayInChannel = matchText(s"say in $ChannelId[:]?(.*)")
  private val FuckOff = matchText("fuck off.*")
  private val Sup = matchText("sup (\\S+).*")
  private val SupAtMention = matchText(s"sup $UserId.*")
  private val FuckYou = matchText("fuck you.*")
  private val WhatTimeIsIt = matchText("what time is it.*")

  private val NumberStrings =
    Array("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {
    case message@IncomingMessage("sup", true, _, _, _)  =>
      message.scheduleResponse(1.seconds, s"What's up homie! $cheerful")

    case message@IncomingMessage(CountToN(number), true, _, _, _) =>
      if (number.toInt > NumberStrings.length - 1) {
        message.respond(s"I can only count to ${NumberStrings.length - 1}!")
      } else {
        (1 to number.toInt).map(i => i -> NumberStrings(i)).foreach {
          tuple =>
            message.scheduleMessage(tuple._1.seconds, s"${tuple._2}!")
        }
      }

    case message@IncomingMessage(CountDownFromN(number), true, _, _, _) =>
      val start = number.toInt + 1
      if (start > NumberStrings.length) {
        message.respond(s"I can only count down from ${NumberStrings.length - 1}!")
      } else {
        (1 to start).map(i => i -> NumberStrings(start - i)).foreach {
          tuple =>
            message.scheduleMessage(tuple._1.seconds, s"${tuple._2}!")
        }
      }

    case message@IncomingMessage(TellColon(recipientUserId, what), true, _, _, _) =>
      tell(message, recipientUserId, what)

    case message@IncomingMessage(TellTo(recipientUserId, what), true, _, _, _) =>
      tell(message, recipientUserId, what)

    case message@IncomingMessage(TellHe(recipientUserId, what), true, _, _, _) =>
      tell(message, recipientUserId, "you " + what)

    case message@IncomingMessage(TellShe(recipientUserId, what), true, _, _, _) =>
      tell(message, recipientUserId, "you " + what)

    case message@IncomingMessage(Sup(name), _, _, _, _) if name == state.self.name =>
      message.respond("What is up!!")

    case message@IncomingMessage(SupAtMention(userId), _, _, UserSender(sentByUser), _) if userId == state.self.id =>
      message.say(s"What is up, <@${sentByUser.id}>.")

    case message@IncomingMessage(SayInChannel(channelId, what), true, _, _, _) =>
      sendMessage(OutgoingMessage(Channel.forChannelId(state, channelId), what))
      message.respond(s"Message sent.")

    case message@IncomingMessage(FuckOff(), _, _, _, _) =>
      message.respond("Same to you.")

    case message@IncomingMessage(FuckYou(), _, _, _, _) =>
      message.respond("This is the worst kind of discrimination there is: the kind against me!")

    case message@IncomingMessage(WhatTimeIsIt(), _, _, _, _) =>
      val format = DateFormat.getDateTimeInstance
      val formatted = format.format(new Date())
      message.respond(s"Here, it is $formatted")
  }

  private def tell(message: IncomingMessage, recipientUserId: String, what: String): Unit = {
    if (recipientUserId == state.self.id) {
      message.respond(s"Dude. I can't talk to myself. $puzzled")
    } else {
      state.getUserById(recipientUserId) match {
        case Some(user) =>
          state.ims.find(_.user == user.id) match {
            case Some(im) =>
              sendMessage(OutgoingMessage(InstantMessageChannel(im.id, user), what))
              message.respond(s"Message sent.")
            case None =>
              val newMessage = message.copy()
              context.system.eventStream.publish(OpenIM(recipientUserId, self, newMessage))
              log.info(s"Opening IM channel to ${user.name}")
          }
        case None =>
          message.respond(s"I don't know who that is. $puzzled")
      }
    }
  }
}
