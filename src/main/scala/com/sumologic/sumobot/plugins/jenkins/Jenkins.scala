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
import com.sumologic.sumobot.Receptionist.BotMessage
import com.sumologic.sumobot.plugins.BotPlugin

object Jenkins {
  def props(name: String, client: JenkinsJobClient): Props =
    Props(classOf[Jenkins], name, client)
}

class Jenkins(val name: String,
              client: JenkinsJobClient)
  extends BotPlugin with ActorLogging {

  override protected def help: String =
    s"""
      |I can build and check on $name jobs:
      |
      |$name status <jobname> - Check the status of the given job.
      |$name build <jobname> - Build the given job.
    """.stripMargin

  private val JobStatus = matchText(s"$name status (\\S+)")
  private val BuildJob = matchText(s"$name build (\\S+)")
  private val Info = matchText(s"$name info")

  import context.dispatcher

  override protected def receiveBotMessage: ReceiveBotMessage = {

    case botMessage @ BotMessage(JobStatus(jobName), _, _, _) =>
      botMessage.respondInFuture {
        msg =>
          client.jobs.find(_._2.getName.trim.toLowerCase == jobName.trim.toLowerCase) match {
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
      }

    case botMessage @ BotMessage(BuildJob(givenName), _, _, _) =>
      val triggeredBy = botMessage.senderName.getOrElse("unknown user")
      val cn = botMessage.channelName orElse botMessage.imName getOrElse s"unknown: ${botMessage.slackMessage.channel}"
      val cause = URLEncoder.encode(s"Triggered via sumobot by $triggeredBy in $cn", "UTF-8")
      botMessage.respondInFuture {
        msg =>
          msg.response(client.buildJob(givenName, cause))
      }
  }
}

