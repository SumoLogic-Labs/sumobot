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

import akka.actor.ActorLogging
import com.atlassian.jira.rest.client.api.domain.Issue
import com.sumologic.sumobot.plugins.jira.Jira.{JiraAvailable, JiraDown, PingJira}
import com.sumologic.sumobot.core.model.{IncomingMessage, OutgoingMessage}
import com.sumologic.sumobot.core.util.TimeHelpers
import com.sumologic.sumobot.plugins.BotPlugin
import org.joda.time.format.PeriodFormatterBuilder
import org.joda.time.{Period, PeriodType}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

object Jira {

  private[jira] val JiraInfo = BotPlugin.matchText("jira ([a-zA-Z]+\\-\\d+)\\s*")
  private[jira] val InProgressJirasFor = BotPlugin.matchText("in\\s?progress jiras for (.+?)")

  case object PingJira

  case object JiraDown

  case object JiraAvailable

}

/**
 * @author Chris (chris@sumologic.com)
 */
class Jira extends BotPlugin with TimeHelpers with ActorLogging {

  override protected def help: String =
    """Communicate with JIRA about stuff
      |
      |jira <issue> - I'll tell you more about that issue.
      |in progress jiras for <user> - I'll tell you what they're working on.
    """.stripMargin

  private val client = new JiraClient(JiraClient.createRestClient(config))

  private val MaxDescLength = 5000 // Maximum description length for now

  private var jiraWentDownAt: Option[Long] = None

  private var downNotificationSent = false

  private var lastJiraCheck: Long = _

  private val timePeriodFormatter = new PeriodFormatterBuilder().
    appendDays().appendSuffix("d").
    appendHours().appendSuffix("h").
    appendMinutes().appendSuffix("m").
    appendSeconds().appendSuffix("s").
    toFormatter


  override protected def pluginPreStart(): Unit = {
    scheduleActorMessage(self.path.name + "-ping-jira", "0 * * ? * *", PingJira)
  }

  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {
    case message@IncomingMessage(Jira.JiraInfo(id), _, _, _) => message.respondInFuture(loadJiraInfo(_, id))
    case message@IncomingMessage(Jira.InProgressJirasFor(username), _, _, _) => message.respondInFuture(loadInProgressJirasFor(_, username))
  }

  private def loadJiraInfo(msg: IncomingMessage, id: String): OutgoingMessage = {
    val jiraInfoFuture: Future[Issue] = client.getIssue(id)
    val issueTry = Try(Await.result(jiraInfoFuture, Duration.apply(10, "seconds")))

    issueTry match {
      case Success(issue) =>
        val string =
          s"""
             |*Title:* ${issue.getSummary}\n
                                            |*Assignee:* ${issue.getAssignee.getName}\n
                                                                                       |*Description:* ${Option(issue.getDescription).map(_.take(MaxDescLength)).getOrElse("no description")}
          """.stripMargin
        msg.response(string)
      case Failure(e) =>
        log.error(e, "Unable to load JIRA issue")
        msg.response(s"Failed: ${e.getMessage}")
    }
  }

  private def loadInProgressJirasFor(msg: IncomingMessage, username: String): OutgoingMessage = {
    val jiraInfoFuture = client.getInProgressIssuesForUser(username)
    val issuesTry = Try(Await.result(jiraInfoFuture, Duration.apply(10, "seconds")))

    issuesTry match {
      case Success(issues) if issues.nonEmpty =>
        val outputString = issues.map {
          issue => s"- ${issue.getKey} - P${issue.getPriority.getId} - ${issue.getSummary}"
        }.mkString("\n")
        msg.response(s"Here are the in progress JIRAs for $username:\n $outputString")
      case Success(issues) =>
        msg.response(s"The user $username isn't working on any JIRAs")
      case Failure(e) =>
        log.error(e, "Unable to load JIRA issue")
        msg.response(s"Failed: ${e.getMessage}")
    }
  }

  override protected def pluginReceive: Receive = {
    case PingJira =>
      if (elapsedSince(lastJiraCheck) > 8.seconds) {

        log.debug("Received message to ping Jira.")

        lastJiraCheck = now

        client.getIssue("SUMO-1").onComplete {
          case Success(issue) =>
            log.info(s"Jira is available. (${issue.getReporter.getName} filed SUMO-1)")
            self ! JiraAvailable
          case Failure(cause) =>
            log.info(s"Jira is down (${cause.getMessage}).")
            self ! JiraDown
            context.system.scheduler.scheduleOnce(10.seconds, self, PingJira)
        }
      }

    case JiraDown =>
      if (jiraWentDownAt.isEmpty) {
        log.info("Jira could not be reached!")
        jiraWentDownAt = Some(now)
      }


      if (!downNotificationSent && elapsedSince(jiraWentDownAt.get) > 3.minutes) {
        downNotificationSent = true
        publicChannel("helpdesk").foreach {
          channel =>
            sendMessage(OutgoingMessage(channel, "Jira seems unreachable. Apologies, let's hope it returns shortly."))
        }
      }


    case JiraAvailable if jiraWentDownAt.isDefined =>
      log.info("Jira is back online!")
      jiraWentDownAt = None
      if (downNotificationSent) {
        downNotificationSent = false
        publicChannel("helpdesk").foreach {
          channel =>
            val downtimeFormatted = timePeriodFormatter.print(new Period(now - jiraWentDownAt.get).normalizedStandard(PeriodType.dayTime()))
            sendMessage(OutgoingMessage(channel, s"Jira seems is back. It was down for $downtimeFormatted"))
        }
      }
  }
}
