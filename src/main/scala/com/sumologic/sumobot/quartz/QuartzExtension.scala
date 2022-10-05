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
package com.sumologic.sumobot.quartz

import akka.actor._
import org.quartz.CronScheduleBuilder._
import org.quartz.JobBuilder._
import org.quartz.TriggerBuilder._
import org.quartz.impl.StdSchedulerFactory
import org.quartz.{JobDataMap, JobExecutionContext}

import java.util.TimeZone

object QuartzExtension
  extends ExtensionId[QuartzExtension]
  with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): QuartzExtension = new QuartzExtension

  override def lookup() = QuartzExtension
}

class QuartzExtension extends Extension {

  private val scheduler = StdSchedulerFactory.getDefaultScheduler

  def scheduleMessage(name: String, cronExpression: String, actor: ActorRef, message: AnyRef): Unit = {
    ensureStarted()

    val job = newJob(classOf[ActorSenderJob]).
      withIdentity(name).
      build()

    val map = new JobDataMap()
    map.put(ActorSenderJob.ActorRef, actor)
    map.put(ActorSenderJob.Message, message)

    val trigger = newTrigger().
      withIdentity(name).
      usingJobData(map).
      withSchedule(cronSchedule(cronExpression).inTimeZone(TimeZone.getDefault)).
      build()

    scheduler.scheduleJob(job, trigger)
  }

  private def ensureStarted(): Unit = {
    if (!scheduler.isStarted) {
      scheduler.start()
    }
  }
}

object ActorSenderJob {
  val ActorRef = "ActorRef"
  val Message = "Message"
}

class ActorSenderJob extends org.quartz.Job {
  override def execute(jobExecutionContext: JobExecutionContext): Unit = {
    val actor = jobExecutionContext.getMergedJobDataMap.get(ActorSenderJob.ActorRef).asInstanceOf[ActorRef]
    val message = jobExecutionContext.getMergedJobDataMap.get(ActorSenderJob.Message)
    actor ! message
  }
}