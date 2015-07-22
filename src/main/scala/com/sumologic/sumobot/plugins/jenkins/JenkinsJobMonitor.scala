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

import akka.actor.Actor
import com.offbytwo.jenkins.model.Job
import com.sumologic.sumobot.core.{Channel, OutgoingMessage}
import com.sumologic.sumobot.plugins.jenkins.JenkinsJobMonitor.InspectJobs

object JenkinsJobMonitor {

  case class InspectJobs(jobs: Map[String, Job])

}

class JenkinsJobMonitor(channel: Channel, jobName: String)
  extends Actor
  with JenkinsJobHelpers {

  private case class JobStatus(lastBuild: Int, currentlyBuilding: Boolean, lastBuildResult: String)

  private var lastStatus: JobStatus = _

  override def receive: Receive = {
    case InspectJobs(jobs) =>
      jobs.find(_._1.equalsIgnoreCase(jobName)).map(_._2).foreach {
        job =>
          val newStatus = toStatus(job)
          if (newStatus != lastStatus) {
            if (lastStatus != null) {
              context.system.eventStream.publish(OutgoingMessage(channel, s"Update for job $jobName: " + summarizeJob(job)))
            }
            lastStatus = newStatus
          }
      }
  }

  private def toStatus(job: Job): JobStatus = {
    val lastBuild = job.details().getLastBuild
    val lastBuildNumber = lastBuild.getNumber
    val currentlyBuilding = lastBuild.details().isBuilding
    val lastBuildResult = lastBuild.details().getResult.toString

    JobStatus(lastBuildNumber, currentlyBuilding, lastBuildResult)
  }
}
