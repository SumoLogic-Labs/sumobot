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
package com.sumologic.sumobot.plugins

import com.sumologic.sumobot.test.annotated.SumoBotSpec
import slack.models.{Im, User, Group, Team, Channel => SlackChannel}
import slack.api.RtmStartState
import slack.rtm.RtmState
import com.sumologic.sumobot.test.annotated.BotPluginTestKit
import akka.actor.{ActorSystem, Props}
import akka.testkit.TestActorRef
import com.sumologic.sumobot.core.model.IncomingMessage
import org.scalatest.BeforeAndAfterEach
import com.sumologic.sumobot.core.model.InstantMessageChannel

class BotPluginUT extends BotPlugin {
  override protected def help: String = "help msg"
  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {
    case message@IncomingMessage(_, _, _, _, _, _, _) =>
      message.respond("Thanks for the message")
  }

  def setState(state: RtmState) = { this.state = state }
}

class BotPluginTest
  extends BotPluginTestKit(ActorSystem("BotPluginTest")) with BeforeAndAfterEach {

  private val self = User("U123", "bender", None, None, None, None, None, None, None, None, None, None, None, None, None, None)
  private val somebodyElse = User("U124", "dude", None, None, None, None, None, None, None, None, None, None, None, None, None, None)
  private val team = Team("T123", "testers", "example.com", "example.com", 1, false, null, "no plan")
  private val channel = SlackChannel("C123", "slack_test", 1, Some(self.id), Some(false), Some(true), Some(false), Some(false), None, None, None, None, None, None, None, None, None, None, None, None)
  private val group = Group("G123", "privatestuff", true, 1, self.id, false, Some(List(self.id, somebodyElse.id)), null, null, None, None, None, None)
  private val im = Im("I123", true, somebodyElse.id, 1, None)
  private val startState = RtmStartState("http://nothing/", self, team, users = List(self, somebodyElse), channels = List(channel), groups = List(group), ims = List(im), List.empty)
  val state = new RtmState(startState)

  "userByName" should {
    "return valid user" in {
      val actorRef = TestActorRef[BotPluginUT]
      val actor = actorRef.underlyingActor
      actor.setState(state)

      actor.userByName(somebodyElse.name) should be(Some(somebodyElse))
      actor.userByName(self.name) should be(Some(self))
    }

    "return None for a non-existent user" in {
      val actorRef = TestActorRef[BotPluginUT]
      val actor = actorRef.underlyingActor
      actor.setState(state)

      actor.userByName("nonexistent") should be(None)
    }
  }

  "instantMessageChannel" should {
    "return IM channel for existing user" in {
      val actorRef = TestActorRef[BotPluginUT]
      val actor = actorRef.underlyingActor
      actor.setState(state)

      actor.instantMessageChannel("dude") should be(Some(InstantMessageChannel("I123", somebodyElse)))
    }

    "return None for non-existent user" in {
      val actorRef = TestActorRef[BotPluginUT]
      val actor = actorRef.underlyingActor
      actor.setState(state)

      actor.instantMessageChannel("lude") should be(None)
    }

  }

}
