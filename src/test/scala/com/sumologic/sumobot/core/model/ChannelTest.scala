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
package com.sumologic.sumobot.core.model

import com.sumologic.sumobot.test.annotated.SumoBotSpec
import slack.api.RtmStartState
import slack.models.{Group, Im, Team, User, Channel => SlackChannel}
import slack.rtm.RtmState

class ChannelTest
  extends SumoBotSpec {

  private val self = User("U123", "bender", None, None, None, None, None, None, None, None, None, None, None, None, None, None)
  private val somebodyElse = User("U124", "dude", None, None, None, None, None, None, None, None, None, None, None, None, None, None)
  private val team = Team("T123", "testers", "example.com", "example.com", 1, false, null, "no plan")
  private val channel = SlackChannel("C123", "slack_test", 1, Some(self.id), Some(false), Some(true), Some(false), Some(false), None, None, None, None, None, None, None, None, None, None, None, None)
  private val group = Group("G123", "privatestuff", true, 1, self.id, false, Some(List(self.id, somebodyElse.id)), null, null, None, None, None, None)
  private val im = Im("I123", true, somebodyElse.id, 1, None)
  private val startState = RtmStartState("http://nothing/", self, team, users = List(self, somebodyElse), channels = List(channel), groups = List(group), ims = List(im), List.empty)
  val state = new RtmState(startState)

  "forChannelId" should {
    "return a public channel" in {
      Channel.forChannelId(state, channel.id) should be (PublicChannel(channel.id, channel.name))
    }

    "return a group channel" in {
      Channel.forChannelId(state, group.id) should be (GroupChannel(group.id, group.name))
    }

    "return a instant message channel" in {
      Channel.forChannelId(state, im.id) should be (InstantMessageChannel(im.id, somebodyElse))
    }

    "throw IllegalArgumentException for unknown channels" in {
      a [IllegalArgumentException] should be thrownBy {
        Channel.forChannelId(state, "humbug")
      }
    }
  }
}
