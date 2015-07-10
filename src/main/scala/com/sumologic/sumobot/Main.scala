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
package com.sumologic.sumobot

import akka.actor.{ActorRef, Props, ActorSystem}
import com.sumologic.sumobot.Bender.AddPlugin
import com.sumologic.sumobot.plugins.aws.AWSCredentialSource
import com.sumologic.sumobot.plugins.awssupport.AWSSupport
import com.sumologic.sumobot.plugins.beer.Beer
import com.sumologic.sumobot.plugins.conversations.Conversations
import com.sumologic.sumobot.plugins.jenkins.{Jenkins, JenkinsJobClient}
import com.sumologic.sumobot.plugins.jira.{Jira, JiraClient}
import com.sumologic.sumobot.plugins.pagerduty.{PagerDuty, PagerDutySchedulesManager}
import com.sumologic.sumobot.plugins.upgradetests.UpgradeTestRunner
import slack.rtm.SlackRtmClient
import scala.concurrent.duration._

object Main extends App {

  sys.env.get("SLACK_API_TOKEN") match {
    case Some(token) =>
      implicit val system = ActorSystem("root")
      val rtmClient = SlackRtmClient(token, 15.seconds)
      val bender = system.actorOf(Bender.props(rtmClient), "bot")
      setupPlugins(bender)
    case None =>
      println(s"Please set the SLACK_API_TOKEN environment variable!")
      sys.exit(1)
  }

  private def setupPlugins(bender: ActorRef)(implicit system: ActorSystem): Unit = {

    JenkinsJobClient.createClient("jenkins").foreach {
      jenkinsClient =>
        bender ! AddPlugin(system.actorOf(props = Jenkins.props("jenkins", jenkinsClient)))
    }

    JenkinsJobClient.createClient("hudson").foreach {
      hudsonClient =>
        bender ! AddPlugin(system.actorOf(props = Jenkins.props("hudson", hudsonClient)))
        bender ! AddPlugin(system.actorOf(Props(classOf[UpgradeTestRunner], hudsonClient), "upgrade-test-runner"))
    }

    PagerDutySchedulesManager.createClient().foreach {
      pagerDutySchedulesManager =>
        bender ! AddPlugin(system.actorOf(Props(classOf[PagerDuty], pagerDutySchedulesManager), "pagerduty"))
    }

    JiraClient.createClient.foreach {
      jiraClient =>
        bender ! AddPlugin(system.actorOf(Props(classOf[Jira], jiraClient), "jira"))
    }

    bender ! AddPlugin(system.actorOf(Props(classOf[Conversations]), "conversations"))
    bender ! AddPlugin(system.actorOf(Props(classOf[Beer]), "beer"))

    val awsCreds = AWSCredentialSource.credentials
    if (awsCreds.nonEmpty) {
      bender ! AddPlugin(system.actorOf(Props(classOf[AWSSupport], awsCreds), "aws-support"))
    }
  }
}
