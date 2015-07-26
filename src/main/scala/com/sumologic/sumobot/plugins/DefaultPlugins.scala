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
import com.sumologic.sumobot.plugins.alias.Alias
import com.sumologic.sumobot.core.aws.AWSCredentialSource
import com.sumologic.sumobot.plugins.awssupport.AWSSupport
import com.sumologic.sumobot.plugins.beer.Beer
import com.sumologic.sumobot.plugins.brain.BrainSurgery
import com.sumologic.sumobot.plugins.conversations.Conversations
import com.sumologic.sumobot.plugins.help.Help
import com.sumologic.sumobot.plugins.system.System
import com.sumologic.sumobot.plugins.jenkins.{Jenkins, JenkinsJobClient}
import com.sumologic.sumobot.plugins.jira.{Jira, JiraClient}
import com.sumologic.sumobot.plugins.pagerduty.{PagerDuty, PagerDutySchedulesManager}
import com.sumologic.sumobot.plugins.tts.TextToSpeech

object DefaultPlugins extends PluginCollection {

  def setup(implicit system: ActorSystem): Unit = {

    addPlugin("help", Props(classOf[Help]))
    addPlugin("conversations", Props(classOf[Conversations]))
    addPlugin("beer", Props(classOf[Beer]))
    addPlugin("system", Props(classOf[System]))
    addPlugin("brain-surgery", Props(classOf[BrainSurgery]))
    addPlugin("alias", Props(classOf[Alias]))

    TextToSpeech.propsOption.foreach {
      props =>
        addPlugin("text-to-speech", props)
    }

    JenkinsJobClient.createClient("jenkins").foreach {
      jenkinsClient =>
        addPlugin("jenkins", Jenkins.props(jenkinsClient))
    }

    JenkinsJobClient.createClient("hudson").foreach {
      hudsonClient =>
        addPlugin("hudson", Jenkins.props(hudsonClient))
    }

    PagerDutySchedulesManager.createClient().foreach {
      pagerDutySchedulesManager =>
        addPlugin("pagerduty", Props(classOf[PagerDuty], pagerDutySchedulesManager, None))
    }

    JiraClient.createClient.foreach {
      jiraClient =>
        addPlugin("jira", Props(classOf[Jira], jiraClient))
    }

    val awsCreds = AWSCredentialSource.credentials
    if (awsCreds.nonEmpty) {
      addPlugin("aws-support", Props(classOf[AWSSupport], awsCreds))
    }
  }
}
