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
package com.sumologic.sumobot.plugins.jira

import java.net.URI

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import com.atlassian.jira.rest.client.api.{UserRestClient, JiraRestClient}
import com.atlassian.jira.rest.client.api.domain.{User => JiraUser}
import com.atlassian.util.concurrent.Promises
import com.sumologic.sumobot.core.Receptionist.RtmStateResponse
import com.sumologic.sumobot.test.SumoBotSpec
import com.typesafe.config.ConfigFactory
import org.scalatest.mock.MockitoSugar
import slack.api.RtmStartState
import slack.models.{Channel, Im, Team, User => SlackUser}
import slack.rtm.RtmState
import org.mockito.Mockito._

class JiraUserMappingTest(_system: ActorSystem)
  extends TestKit(_system) with SumoBotSpec with MockitoSugar {

  def this() = {
    this(ActorSystem("JiraUserMappingTest", ConfigFactory.parseString(
      """plugins {
        |  jira {
        |    mapping {
        |      firstlast = "first"
        |    }
        |  }
        |}
      """.stripMargin)))
  }


  val jiraClient = mock[JiraRestClient]
  val userClient = mock[UserRestClient]
  when(jiraClient.getUserClient).thenReturn(userClient)
  val avatarUris = new java.util.HashMap[String, URI]()
  avatarUris.put(JiraUser.S48_48, new URI("http://www.sumologic.com"))
  val jiraUserObject = new JiraUser(null, "first", null, null, null, avatarUris, null)
  when(userClient.getUser("first")).thenReturn(Promises.promise(jiraUserObject))

  val ref = system.actorOf(Props(classOf[JiraUserMapping], jiraClient))

  private val self = new SlackUser("U123", "bender", None, None, None, None, None, None, None, None, None, None, None, None, None, None)
  private val slackUserObject = new SlackUser("U124", "firstlast", None, None, None, None, None, None, None, None, None, None, None, None, None, None)
  private val team = new Team("T123", "testers", "example.com", "example.com", 1, false, null, "no plan")
  private val channel = new Channel("C123", "slack_test", 1, self.id, Some(false), Some(true), Some(false), None, None, None, None, None, None, None,None, None, None, None)
  private val im = new Im("I123", true, slackUserObject.id, 1, None)
  private val startState = new RtmStartState("http://nothing/", self, team, users = List(self, slackUserObject), channels = List(channel), List.empty, ims = List(im), List.empty)

  val state = new RtmState(startState)

  ref ! RtmStateResponse(state)

  val sut = new BlockingJiraUserMapping(ref)

  "JiraUserMapping" should {
    "correctly map jira users to slack users" in {
      sut.jiraToSlack(jiraUserObject).get.name should be("firstlast")
    }

    "correctly map slack users to jira users" in {
      sut.slackToJira(slackUserObject).get.getName should be("first")
    }
  }
}
