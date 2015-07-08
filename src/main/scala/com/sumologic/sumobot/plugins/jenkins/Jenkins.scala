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
package com.sumologic.sumobot.plugins.jenkins

import java.net.URLEncoder

import akka.actor.{ActorLogging, Props}
import akka.pattern.pipe
import com.offbytwo.jenkins.model.Job
import com.sumologic.sumobot.Bender.SendSlackMessage
import com.sumologic.sumobot.plugins.BotPlugin
import slack.rtm.RtmState

import scala.concurrent.Future

object Jenkins {
  def props(state: RtmState, name: String, client: JenkinsJobClient): Props =
    Props(classOf[Jenkins], state, name, client)
}

class Jenkins(state: RtmState,
              val name: String,
              client: JenkinsJobClient)
  extends BotPlugin with ActorLogging {

  override protected def help: String =
    s"""
      |Allows triggering and checking on $name jobs:
      |
      |$name status <jobname> - Checks the status of the given job.
      |$name trigger <jobname> - Triggers the given job.
    """.stripMargin

  private val JobStatus = matchText(s"$name status (\\S+)")
  private val TriggerJob = matchText(s"$name trigger (\\S+)")
  private val Info = matchText(s"$name info")

  import context.dispatcher

  override protected def receiveText: ReceiveText = {

    case JobStatus(jobName) =>
      val msg = botMessage
      jobs.map {
        jobMap =>
          jobMap.find(_._2.getName.trim.toLowerCase == jobName.trim.toLowerCase) match {
            case Some(tuple) =>
              val job = tuple._2
              val buildDetails = job.details().getLastBuild.details()
              val buildNo = job.details().getLastBuild.getNumber
              val status = if (buildDetails.isBuilding) {
                s"building (#$buildNo, elapsed: ${buildDetails.getDuration}, estimated: ${buildDetails.getEstimatedDuration})"
              } else {
                s"${buildDetails.getResult.name().toLowerCase} (#$buildNo, took: ${buildDetails.getDuration})"
              }
              msg.response(s"$name job ${job.getName} status: $status")
            case None =>
              msg.response(client.unknownJobMessage(jobName))
          }
      } pipeTo sender()

    case TriggerJob(givenName) =>
      val triggeredBy = state.users.find(_.id == botMessage.slackMessage.user).map(_.name).getOrElse("unknown user")
      val channelName = state.channels.find(_.id == botMessage.slackMessage.channel).map(_.name)
        .orElse(state.ims.find(_.id == botMessage.slackMessage.channel).map(_.user)).getOrElse(s"unknown: ${botMessage.slackMessage.channel}")
      val cause = URLEncoder.encode(s"Triggered via sumobot by $triggeredBy in $channelName", "UTF-8")
      val msg = botMessage
      Future[SendSlackMessage] {
        msg.response(client.triggerJob(givenName, cause))
      } pipeTo sender()
  }

  private def jobs: Future[Map[String, Job]] = {
    log.info("jobs called")
    Future(client.jobs)
  }
}

