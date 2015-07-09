package com.sumologic.sumobot.plugins.jira

import akka.actor.ActorLogging
import com.atlassian.jira.rest.client.api.domain.Issue
import com.sumologic.sumobot.Bender.{SendSlackMessage, BotMessage}
import com.sumologic.sumobot.plugins.BotPlugin
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

import scala.concurrent.{Await, Future}
import scala.util.{Try, Failure, Success}

/**
 * @author Chris (chris@sumologic.com)
 */
class Jira(client: JiraClient) extends BotPlugin with ActorLogging {

  private val MaxDescLength = 5000 // Maximum description length for now

  private val JiraInfo = matchText("jira (.+?)")
  override protected def receiveText: ReceiveText = {
    case JiraInfo(id) => respondInFuture(loadJiraInfo(_, id))
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

  override protected def name: String = "jira"

  override protected def help: String = "Communicate with JIRA about stuff"
}
