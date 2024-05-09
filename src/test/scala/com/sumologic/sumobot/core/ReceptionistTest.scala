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
package com.sumologic.sumobot.core

import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.testkit.{TestKit, TestProbe}
import com.sumologic.sumobot.brain.InMemoryBrain
import com.sumologic.sumobot.core.Receptionist.{RtmStateRequest, RtmStateResponse}
import com.sumologic.sumobot.core.model.{IncomingMessage, OpenIM, OutgoingMessage}
import com.sumologic.sumobot.plugins.BotPlugin.{InitializePlugin, PluginAdded}
import com.sumologic.sumobot.test.annotated.SumoBotTestKit
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import slack.api.{BlockingSlackApiClient, RtmConnectState, SlackApiClient}
import slack.models._
import slack.rtm.{RtmState, SlackRtmClient}

import scala.concurrent.duration._

class ReceptionistTest
  extends SumoBotTestKit(ActorSystem("ReceptionistTest"))
  with MockitoSugar
  with BeforeAndAfterEach
  with BeforeAndAfterAll {

  private val self = User("U123", "bender", None, None, None, None, None, None, None, None, None, None, None, None, None, None)
  private val somebodyElse = User("U124", "dude", None, None, None, None, None, None, None, None, None, None, None, None, None, None)
  private val team = Team("T123", "testers", "example.com")
  private val channel = Channel("C123", Some("slack_test"), 1, Some(self.id), Some(false), Some(true), Some(false), Some(false), None, None, None, None, None, None, None, None, None, None, None, None)
  private val im = Im("D123", true, somebodyElse.id, 1, None)
  private val startState = RtmConnectState(ok = true, "http://nothing/", self, team)

  val state = new RtmState(startState)
  val rtmClient = mock[SlackRtmClient]
  val syncClient = mock[BlockingSlackApiClient]
  val asyncClient = mock[SlackApiClient]
  when(rtmClient.state).thenReturn(state)

  private val probe = new TestProbe(system)
  system.eventStream.subscribe(probe.ref, classOf[IncomingMessage])
  system.eventStream.subscribe(probe.ref, classOf[OutgoingMessage])
  private val brain = system.actorOf(Props(classOf[InMemoryBrain]), "brain")
  private val sut = system.actorOf(Props(new Receptionist(rtmClient, syncClient, asyncClient, brain){
    override def fetchUsers(): Seq[User] = Seq()
  }))

  "Receptionist" should {
    "mark messages as addressed to us" when {
      "message starts with @mention" in {
        sut ! Message(currentTimeStamp, channel.id, Some(somebodyElse.id), s"<@${self.id}> hello dude1", None, None, None, None, None)
        val result = probe.expectMsgClass(classOf[IncomingMessage])
        result.canonicalText should be("hello dude1")
        result.addressedToUs should be(true)
      }

      "message starts with our name" in {
        sut ! Message(currentTimeStamp, channel.id, Some(somebodyElse.id), s"${self.name} hello dude2", None, None, None, None, None)
        val result = probe.expectMsgClass(classOf[IncomingMessage])
        result.canonicalText should be("hello dude2")
        result.addressedToUs should be(true)
      }

      "message is an instant message" in {
        sut ! Message(currentTimeStamp, im.id, Some(somebodyElse.id), "hello dude3", None, None, None, None, None)
        val result = probe.expectMsgClass(classOf[IncomingMessage])
        result.canonicalText should be("hello dude3")
        result.addressedToUs should be(true)
      }
    }

    "mark message as not addressed to us otherwise" in {
      sut ! Message(currentTimeStamp, channel.id, Some(somebodyElse.id), "just a message", None, None, None, None, None)
      val result = probe.expectMsgClass(classOf[IncomingMessage])
      result.canonicalText should be("just a message")
      result.addressedToUs should be(false)
    }

    "re-interpret messages that were updated" in {

      val previousMessage = EditMessage(Some(somebodyElse.id), "previous message", currentTimeStamp, None)
      val newMessage = EditMessage(Some(somebodyElse.id), "hello dude4", currentTimeStamp, None)

      sut ! MessageChanged(newMessage, previousMessage, currentTimeStamp, currentTimeStamp, channel.id)
      val result = probe.expectMsgClass(classOf[IncomingMessage])
      result.canonicalText should be("hello dude4")
      result.addressedToUs should be(false)
    }

    "route message when timestamp cannot be parsed" in {
      sut ! Message("humbug", channel.id, Some(somebodyElse.id), "just a message", None, None, None, None, None)
      val result = probe.expectMsgClass(classOf[IncomingMessage])
      result.canonicalText should be("just a message")
      result.addressedToUs should be(false)
    }

    "drop a message" when {

      "the time stamp is older than 60 seconds" in {
        val now = System.currentTimeMillis()
        val tooLongAgo = (now - (1000 * 61))/1000
        sut ! Message(s"$tooLongAgo.000005", im.id, Some(somebodyElse.id), "just a message", None, None, None, None, None)
        probe.expectNoMessage(1.second)
      }

      "it originated from our user" in {
        sut ! Message(currentTimeStamp, channel.id, Some(self.id), s"This is me!", None, None, None, None, None)
        probe.expectNoMessage(1.second)
      }
    }

    "initialize plugins that are added" in {
      sut ! PluginAdded(probe.ref, "")
      val initMessage = probe.expectMsgClass(classOf[InitializePlugin])
      initMessage.brain should be(brain)
      initMessage.state should be(state)
      initMessage.pluginRegistry should not be (null)
    }

    "open a new IM channel asynchronously when asked" in {
      case object DoneWithThat
      sut ! OpenIM(somebodyElse.id, OutgoingMessage(null, text = "Hello from UT"))
      sut ! ImOpened(somebodyElse.id, im.id)
      probe.expectMsg(OutgoingMessage(im.id, text = "Hello from UT"))
    }

    "return the RTM state when asked" in {
      sut ! RtmStateRequest(probe.ref)
      probe.expectMsgClass(classOf[RtmStateResponse])
    }
  }

  private def currentTimeStamp: String = s"${System.currentTimeMillis()/1000}.000001"

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
