/**
 *    _____ _____ _____ _____    __    _____ _____ _____ _____
 *   |   __|  |  |     |     |  |  |  |     |   __|     |     |
 *   |__   |  |  | | | |  |  |  |  |__|  |  |  |  |-   -|   --|
 *   |_____|_____|_|_|_|_____|  |_____|_____|_____|_____|_____|
 *
 *                UNICORNS AT WARP SPEED SINCE 2010
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sumologic.sumobot.plugins.jenkins

import java.net.URI

import akka.actor.Props
import akka.pattern.pipe
import com.offbytwo.jenkins.JenkinsServer
import com.offbytwo.jenkins.model.Job
import com.sumologic.sumobot.plugins.BotPlugin

import scala.concurrent.Future

object Jenkins {
  def propsOption(name: String): Option[Props] = {
    val nameUpper = name.toUpperCase
    for (url <- sys.env.get(s"${nameUpper}_URL");
         user <- sys.env.get(s"${nameUpper}_USER");
         password <- sys.env.get(s"${nameUpper}_PASSWORD"))
      yield Props(classOf[Jenkins], name, url, user, password)
  }
}

class Jenkins(val name: String, url: String, user: String, password: String)
  extends BotPlugin {

  override protected def help: String =
    s"""
      |Allows triggering and checking on $name jobs:
      |
      |$name status <jobname> - Checks the status of the given job.
      |$name trigger <jobname> - Triggers the given job.
    """.stripMargin

  private val JobStatus = matchText(s"$name status (\\S+)")
  private val TriggerJob = matchText(s"$name trigger (\\S+)")

  import context.dispatcher
  private val server = new JenkinsServer(new URI(url), user, password)
  private val cachedJobs = Map[String, Job]()

  override protected def receiveText: ReceiveText = {

    case JobStatus(jobName) =>
      withJob(jobName) {
        job =>
          Future {
            val buildDetails = job.details().getLastBuild.details()
            val buildNo = job.details().getLastBuild.getNumber
            val status = if (buildDetails.isBuilding) {
              s"building (#$buildNo, elapsed: ${buildDetails.getDuration}, estimated: ${buildDetails.getEstimatedDuration})"
            } else {
              s"${buildDetails.getResult.name().toLowerCase} (#$buildNo, took: ${buildDetails.getDuration})"
            }
            botMessage.response(s"$name job ${job.getName} status: $status")
          } pipeTo sender()
      }

    case TriggerJob(jobName) =>
      withJob(jobName) {
        job =>
          Future {
            val jobWithDetails = server.getJob(jobName)
            val isBuildable = jobWithDetails.isBuildable
            if (!isBuildable) {
              botMessage.response(s"${job.getName} is not buildable.")
            } else {
              // TODO: Jenkins client lib is missing this.
              botMessage.response(s"$name job ${job.getName} build TODO - cannot schedule stuff! ")
            }
          } pipeTo sender()
      }

  }


  private def withJob(jobName: String)(func: Job => Unit): Unit =
    cachedJobs.find(_._2.getName.trim.toLowerCase == jobName.trim.toLowerCase) match {
      case Some(tuple) =>
        func(tuple._2)
      case None =>
        sender() ! botMessage.response(unknownJobMessage(jobName))
    }

  private def unknownJobMessage(jobName: String) = chooseRandom(
    s"I don't know any job named $jobName!! $upset",
    s"Bite my shiny metal ass. There's no job named $jobName!"
  )
}
