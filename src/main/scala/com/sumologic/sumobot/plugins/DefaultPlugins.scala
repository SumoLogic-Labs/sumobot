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
package com.sumologic.sumobot.plugins

import akka.actor.{ActorSystem, Props}
import com.sumologic.sumobot.plugins.aws.AWSCredentialSource
import com.sumologic.sumobot.plugins.awssupport.AWSSupport
import com.sumologic.sumobot.plugins.beer.Beer
import com.sumologic.sumobot.plugins.brain.BrainSurgery
import com.sumologic.sumobot.plugins.conversations.Conversations
import com.sumologic.sumobot.plugins.help.Help
import com.sumologic.sumobot.plugins.info.Info
import com.sumologic.sumobot.plugins.jenkins.{Jenkins, JenkinsJobClient}
import com.sumologic.sumobot.plugins.jira.{Jira, JiraClient}
import com.sumologic.sumobot.plugins.pagerduty.{PagerDuty, PagerDutySchedulesManager}

object DefaultPlugins {

  def setup(implicit system: ActorSystem): Unit = {

    system.actorOf(Props(classOf[Help]), "help")
    system.actorOf(Props(classOf[Conversations]), "conversations")
    system.actorOf(Props(classOf[Beer]), "beer")
    system.actorOf(Props(classOf[Info]), "info")
    system.actorOf(Props(classOf[BrainSurgery]), "brain-surgery")

    JenkinsJobClient.createClient("jenkins").foreach {
      jenkinsClient =>
        system.actorOf(props = Jenkins.props(jenkinsClient), "jenkins")
    }

    JenkinsJobClient.createClient("hudson").foreach {
      hudsonClient =>
        system.actorOf(props = Jenkins.props(hudsonClient), "hudson")
    }

    PagerDutySchedulesManager.createClient().foreach {
      pagerDutySchedulesManager =>
        system.actorOf(Props(classOf[PagerDuty], pagerDutySchedulesManager, None), "pagerduty")
    }

    JiraClient.createClient.foreach {
      jiraClient =>
        system.actorOf(Props(classOf[Jira], jiraClient), "jira")
    }

    val awsCreds = AWSCredentialSource.credentials
    if (awsCreds.nonEmpty) {
      system.actorOf(Props(classOf[AWSSupport], awsCreds), "aws-support")
    }
  }
}
