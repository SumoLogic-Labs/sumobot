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

import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.Issue
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.typesafe.config.Config

import scala.collection.JavaConversions._
import scala.concurrent.Future

/**
 * @author Chris (chris@sumologic.com)
 */
object JiraClient {

  def baseUrl(config: Config): String = config.getString("url")

  def createRestClient(config: Config): JiraRestClient = {
    val url = baseUrl(config)
    val username = config.getString("username")
    val password = config.getString("password")

    val jiraServerUri = new URI(url)
    require(jiraServerUri.getHost != null, "host should not be null")
    val authenticationHandler = new BasicHttpAuthenticationHandler(username, password)
    val factory = new AsynchronousJiraRestClientFactory
    factory.create(jiraServerUri, authenticationHandler)
  }
}

class JiraClient(restClient: JiraRestClient) {

  // TODO(stefan, 2015-07-26): We should decide on how best to switch from java future/atlassian promise to scala Future's

  def getIssue(id: String)(implicit context: scala.concurrent.ExecutionContext): Future[Issue] = {
    val promise = restClient.getIssueClient.getIssue(id)
    Future(promise.get())
  }

  def getInProgressIssuesForUser(username: String)(implicit context: scala.concurrent.ExecutionContext): Future[Seq[Issue]] = {
    issuesForJql(s"assignee = '$username' and status = 'in progress'")
  }

  private def issuesForJql(jql: String)(implicit context: scala.concurrent.ExecutionContext): Future[Seq[Issue]] = {
    Future(blockingIssuesForJql(jql))
  }

  def blockingIssuesForJql(jql: String): Seq[Issue] = {
    restClient.getSearchClient.searchJql(jql).get().getIssues.toSeq
  }
}
