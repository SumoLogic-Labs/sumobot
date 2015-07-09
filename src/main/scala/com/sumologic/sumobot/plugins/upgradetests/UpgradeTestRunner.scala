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
package com.sumologic.sumobot.plugins.upgradetests

import akka.actor.ActorLogging
import com.sumologic.sumobot.plugins.BotPlugin
import com.sumologic.sumobot.plugins.jenkins.JenkinsJobClient
import slack.rtm.RtmState
import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.{Failure, Success, Try}

object UpgradeTestRunner {

  case class AssemblyGroup(name: String, jobs: Seq[String])

  case class Channel(name: String, assemblyGroups: Seq[AssemblyGroup])

}

class UpgradeTestRunner(state: RtmState,
                        jenkinsJobClient: JenkinsJobClient) extends BotPlugin with ActorLogging  {

  import UpgradeTestRunner._

  private val RunTests = matchText("upgrade tests (\\S+).*")

  override protected def name: String = "upgrade-tests"

  override protected def help: String =
    s"""
       |Runs all the jobs required after the upgrade of a given assembly group.
       |
       |upgrade tests <group> - Triggers all the jobs for the given assembly group.
     """.stripMargin

  private val prodChannel = Channel("ops_prod",
    AssemblyGroup("internal", "Prod-Upgrade-Sanity-Check-Email" :: Nil)
      :: Nil)

  private val testChannel = Channel("slack_test",
    AssemblyGroup("internal", "client-lib-Snapshot" :: Nil)
      :: Nil)

  private val channels =
    prodChannel ::
      testChannel ::
      Nil

  override protected def receiveText: ReceiveText = {
    case RunTests(assemblyGroup) =>
      val channelName = botMessage.channelName(state)
      channels.filter(_ => channelName.isDefined).find(_.name == channelName.get) match {
        case Some(channel) =>
          channel.assemblyGroups.find(_.name == assemblyGroup) match {
            case Some(foundGroup) =>
              respondInFuture {
                msg =>
                  val jobsToTrigger = jenkinsJobClient.jobs.filter(job => foundGroup.jobs.contains(job._2.getName)).values
                  val (succeeded, failed) = jobsToTrigger.partition {
                    job =>
                      Try {
                        jenkinsJobClient.triggerJob(job.getName, s"Triggered by ${msg.senderName(state)}")
                      } match {
                        case Success(_) =>
                          true
                        case Failure(e) =>
                          log.error(e, s"Could not start job ${job.getName}")
                          false
                      }
                  }

                  if (failed.nonEmpty) {
                    if (succeeded.isEmpty) {
                      msg.message(s"Could not trigger any jobs!")
                    } else {
                      msg.message(s"Triggered jobs: ${succeeded.map(_.getName).mkString(",")}, but could not trigger: ${failed.map(_.getName).mkString(",")}")
                    }
                  } else {
                    msg.message(s"Triggered jobs: ${succeeded.map(_.getName).mkString(",")}")
                  }
              }
            case None =>
              botMessage.respond(s"I don't know this assembly group. " +
                s"Groups I know for ${channel.name}: ${channel.assemblyGroups.map(_.name).sorted.mkString(",")}")
          }

        case None =>
          botMessage.respond(s"You can't do this in this channel. " +
            s"The only channels I know of are: ${channels.map(_.name).sorted.mkString(",")}")
      }
  }
}
