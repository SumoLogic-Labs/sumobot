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
import com.sumologic.sumobot.Receptionist.{SendSlackMessage, BotMessage}
import com.sumologic.sumobot.plugins.BotPlugin
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

import scala.concurrent.{Await, Future}
import scala.util.{Try, Failure, Success}

/**
 * @author Chris (chris@sumologic.com)
 */
class Jira(client: JiraClient) extends BotPlugin with ActorLogging {

  override protected def name: String = "jira"

  override protected def help: String =
    """Communicate with JIRA about stuff
      |
      |jira <issue> - I'll tell you more about that issue.
      |in progress jiras for <user> - I'll tell you what they're working on.
    """.stripMargin

  private val MaxDescLength = 5000 // Maximum description length for now

  private val JiraInfo = matchText("jira (.+?)")
  private val InProgressJirasFor = matchText("in\\s?progress jiras for (.+?)")
  override protected def receiveBotMessage: ReceiveBotMessage = {
    case botMessage @ BotMessage(JiraInfo(id), _, _, _) => botMessage.respondInFuture(loadJiraInfo(_, id))
    case botMessage @ BotMessage(InProgressJirasFor(username), _, _, _) => botMessage.respondInFuture(loadInProgressJirasFor(_, username))
  }

  private def loadJiraInfo(msg: BotMessage, id: String): SendSlackMessage = {
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

  private def loadInProgressJirasFor(msg: BotMessage, username: String): SendSlackMessage = {
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

}
