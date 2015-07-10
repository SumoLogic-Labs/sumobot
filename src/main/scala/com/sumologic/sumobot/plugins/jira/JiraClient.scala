package com.sumologic.sumobot.plugins.jira

import java.net.URI

import com.atlassian.jira.rest.client.api.domain.Issue
import com.atlassian.jira.rest.client.api.{AuthenticationHandler, JiraRestClient}
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.netflix.config.scala.DynamicStringProperty

import scala.collection.JavaConversions._
import scala.concurrent.Future

/**
 * @author Chris (chris@sumologic.com)
 */
object JiraClient {

  private val Url = DynamicStringProperty("jira.url", null)
  private val Username = DynamicStringProperty("jira.username", null)
  private val Password = DynamicStringProperty("jira.password", null)

  def createClient: Option[JiraClient] = {
    for (username <- Username();
         password <- Password();
         url <- Url())
      yield {
        val jiraServerUri = new URI(url)
        require(jiraServerUri.getHost != null, "host should not be null")
        val authenticationHandler: AuthenticationHandler = new BasicHttpAuthenticationHandler(username, password)
        val factory = new AsynchronousJiraRestClientFactory
        val restClient = factory.create(jiraServerUri, authenticationHandler)
        new JiraClient(restClient)
      }
  }
}

class JiraClient(restClient: JiraRestClient) {

  // TODO: We should decide on how best to switch from java future/atlassian promise to scala Future's

  def getIssue(id: String)(implicit context: scala.concurrent.ExecutionContext): Future[Issue] = {
    val promise = restClient.getIssueClient.getIssue(id)
    Future(promise.get())
  }

  def getInProgressIssuesForUser(username: String)(implicit context: scala.concurrent.ExecutionContext): Future[Seq[Issue]] = {
    issuesForJql(s"assignee = '$username' and status = 'in progress'")
  }

  private def issuesForJql(jql: String)(implicit context: scala.concurrent.ExecutionContext): Future[Seq[Issue]] = {
    Future(restClient.getSearchClient.searchJql(jql).get()).map {
      searchResult => searchResult.getIssues.toSeq
    }
  }
}
