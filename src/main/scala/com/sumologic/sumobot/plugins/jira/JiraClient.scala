package com.sumologic.sumobot.plugins.jira

import java.net.URI

import com.atlassian.jira.rest.client.api.domain.Issue
import com.atlassian.jira.rest.client.api.{AuthenticationHandler, JiraRestClient}
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory

import scala.concurrent.Future

/**
 * @author Chris (chris@sumologic.com)
 */
object JiraClient {
  def createClient: Option[JiraClient] = {
    for (username <- sys.env.get("JIRA_USERNAME");
         password <- sys.env.get("JIRA_PASSWORD");
         url <- sys.env.get("JIRA_URL"))
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

}
