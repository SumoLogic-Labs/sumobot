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

import akka.actor.{ActorLogging, ActorRef, PoisonPill, Props}
import com.offbytwo.jenkins.model.Job
import com.sumologic.sumobot.core.model.{Channel, IncomingMessage, OutgoingMessage, UserSender}
import com.sumologic.sumobot.plugins.BotPlugin
import com.sumologic.sumobot.plugins.jenkins.JenkinsJobMonitor.InspectJobs

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class Jenkins
  extends BotPlugin
    with JenkinsJobHelpers
    with ActorLogging {

  private case object PollJobs

  def name = self.path.name

  override protected def help: String =
    s"""I can build and check on $name jobs:

$name info - I'll tell you which $name instance I'm talking to.
$name status <jobname> - Check the status of the given job.
$name build <jobname> - Build the given job.
$name monitor <jobname> - I'll let you know when something changes about the job.
$name unmonitor <jobname> - I'll stop bugging you about that job."""

  private val JobStatus = matchText(s"$name status (\\S+)")
  private val BuildJob = matchText(s"$name build (\\S+)")
  private val MonitorJob = matchText(s"$name monitor (\\S+)")
  private val UnmonitorJob = matchText(s"$name unmonitor (\\S+)")
  private val Info = matchText(s"$name info")

  private val client: JenkinsJobClient = new JenkinsJobClient(JenkinsConfiguration.load(config))

  private var monitoredJobs = Map.empty[String, ActorRef]

  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {

    case message@IncomingMessage(Info(), _, _, _, _, _, _) =>
      message.respond(s"Connected to ${client.configuration.url}")

    case message@IncomingMessage(JobStatus(givenName), _, _, _, _, _, _) =>
      message.respondInFuture {
        msg =>
          withKnownJob(msg, givenName) {
            job =>
              msg.response(s"$name job ${job.getName} status: ${summarizeJob(job)}")
          }
      }

    case message@IncomingMessage(BuildJob(givenName), _, _, _, UserSender(user), _, _) =>
      val cause = URLEncoder.encode(s"Triggered via sumobot by ${user.name} in ${message.channel.name}", "UTF-8")
      message.respondInFuture {
        msg =>
          msg.response(client.buildJob(givenName, cause))
      }

    case message@IncomingMessage(MonitorJob(givenName), _, _, _, _, _, _) =>
      message.respondInFuture {
        msg =>
          withKnownJob(msg, givenName) {
            job =>
              val key = monitorKey(message.channel, job.getName)
              if (!monitoredJobs.contains(key)) {
                val monitor = context.actorOf(Props(classOf[JenkinsJobMonitor], msg.channel, job.getName), s"monitor-$key")
                val firstMonitoredJob = monitoredJobs.isEmpty
                monitoredJobs += (key -> monitor)
                if (firstMonitoredJob) {
                  scheduleNextPoll()
                }
                msg.response(s"Okie, will update you when ${job.getName} changes. Current: ${summarizeJob(job)}")
              } else {
                msg.response(s"Already monitoring ${job.getName}")
              }
          }
      }

    case message@IncomingMessage(UnmonitorJob(givenName), _, _, _, _, _, _) =>
      monitoredJobs.find(_._1.equalsIgnoreCase(monitorKey(message.channel, givenName.trim))) match {
        case Some(monitoredJob) =>
          monitoredJobs -= monitoredJob._1
          monitoredJob._2 ! PoisonPill
          message.respond(s"Alright, no longer paying attention to ${monitoredJob._1}")
        case None =>
          message.respond(s"I'm not currently monitoring $givenName")
      }
  }

  private def monitorKey(channel: Channel, jobName: String): String = s"${channel.name}-$jobName"

  override protected def pluginReceive: Receive = {
    case PollJobs =>
      if (monitoredJobs.nonEmpty) {
        scheduleNextPoll()
      }
      val newJobs = client.jobs
      monitoredJobs.values.foreach(_ ! InspectJobs(newJobs))
  }

  private def scheduleNextPoll(): Unit = context.system.scheduler.scheduleOnce(1.second, self, PollJobs)

  private def withKnownJob(msg: IncomingMessage, givenName: String)(func: Job => OutgoingMessage): OutgoingMessage =
    client.jobs.find(_._1.equalsIgnoreCase(givenName.trim)).map(_._2) match {
      case Some(job) =>
        func(job)
      case None =>
        msg.response(client.unknownJobMessage(givenName))
    }
}

