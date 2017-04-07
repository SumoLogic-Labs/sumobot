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

import akka.actor.{Actor, ActorRef}
import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout
import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.{User => JiraUser}
import com.sumologic.sumobot.plugins.jira.JiraUserMapping.{JiraToSlack, SlackToJira, UserMappingResponse, UserNotFound}
import com.sumologic.sumobot.core.Bootstrap
import com.sumologic.sumobot.core.Receptionist.{RtmStateRequest, RtmStateResponse}
import slack.models.{User => SlackUser}
import slack.rtm.RtmState

import collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class BlockingJiraUserMapping(actor: ActorRef) {

  def slackToJira(slackUser: SlackUser): Option[JiraUser] = {
    implicit val timeout = Timeout(2.seconds)
    Try(Await.result(actor ? SlackToJira(slackUser), 2.seconds).asInstanceOf[UserMappingResponse].jiraUser).toOption
  }

  def jiraToSlack(jiraUser: JiraUser): Option[SlackUser] = {
    implicit val timeout = Timeout(2.seconds)
    Try(Await.result(actor ? JiraToSlack(jiraUser), 2.seconds).asInstanceOf[UserMappingResponse].slackUser).toOption
  }
}

object JiraUserMapping {

  case class SlackToJira(slackUser: SlackUser)

  case class JiraToSlack(jiraUser: JiraUser)

  case class UserMappingResponse(slackUser: SlackUser, jiraUser: JiraUser)

  case object UserNotFound

}

class JiraUserMapping(jiraClient: JiraRestClient) extends Actor {

  private val log = Logging.getLogger(Bootstrap.system, this)

  private var jiraToSlackMap = Map[String, SlackUser]()

  private var slackToJiraMap = Map[String, JiraUser]()

  override def receive: Receive = uninitialized

  override def preStart(): Unit = {
    context.system.eventStream.publish(RtmStateRequest(self))
  }

  private def uninitialized: Receive = {
    case RtmStateResponse(rtmState) =>
      refresh(rtmState)
      context.become(initialized(rtmState))
  }

  private def initialized(rtmState: RtmState): Receive = {
    case SlackToJira(slackUser) =>
      slackToJiraMap.get(slackUser.name) match {
        case Some(jiraUser) =>
          sender() ! UserMappingResponse(slackUser, jiraUser)
        case None =>
          UserNotFound
      }

    case JiraToSlack(jiraUser) =>
      jiraToSlackMap.get(jiraUser.getName) match {
        case Some(slackUser) =>
          sender() ! UserMappingResponse(slackUser, jiraUser)
        case None =>
          UserNotFound
      }
  }

  def refresh(rtmState: RtmState): Unit = {

    val jira = jiraClient.getUserClient
    var nextJiraToSlackMap = Map[String, SlackUser]()
    var nextSlackToJiraMap = Map[String, JiraUser]()

    val ignoredUsers = Try(context.system.settings.config.getStringList("jira.ignoreUsers").asScala).
      toOption.getOrElse(List.empty).toSet

    def isSumoUser(user: SlackUser): Boolean = {
      val deleted = user.deleted.getOrElse(false)
      val restricted = user.is_restricted.getOrElse(false)
      val ultraRestricted = user.is_ultra_restricted.getOrElse(false)
      val ignored = ignoredUsers.contains(user.name)
      !(deleted | restricted | ultraRestricted | ignored)
    }

    rtmState.users.filter(isSumoUser).par.foreach {
      slackUser =>
        def namePart(email: String) = email.substring(0, email.indexOf("@"))
        val jiraId = slackUser.profile.
          flatMap(_.email).
          map(namePart).
          getOrElse(slackUser.name)

        val possibleNames = List(jiraId, slackUser.name,
          translatedUserName(jiraId), translatedUserName(slackUser.name)).
          distinct

        val userFound = possibleNames.exists {
          possibleName =>
            Try(jira.getUser(possibleName).claim()) match {
              case Success(jiraUser) =>
                nextJiraToSlackMap += (jiraUser.getName -> slackUser)
                nextSlackToJiraMap += (slackUser.name -> jiraUser)
                true
              case Failure(_) =>
                false
            }
        }

        if (!userFound) {
          log.warning(s"Could not find user ${slackUser.name} in Jira (tried: ${possibleNames.mkString(", ")})")
        }
    }

    jiraToSlackMap = nextJiraToSlackMap
    slackToJiraMap = nextSlackToJiraMap
  }

  private def translatedUserName(userName: String): String = {
    Try(context.system.settings.config.getString(s"jira.mapping.$userName")).toOption.getOrElse(userName)
  }
}
