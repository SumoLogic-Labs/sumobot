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

import java.net.{URI, URLEncoder}

import akka.actor.{ActorLogging, Props}
import akka.pattern.pipe
import com.offbytwo.jenkins.JenkinsServer
import com.offbytwo.jenkins.client.JenkinsHttpClient
import com.offbytwo.jenkins.model.Job
import com.sumologic.sumobot.Bender.SendSlackMessage
import com.sumologic.sumobot.plugins.BotPlugin
import org.apache.http.HttpResponse
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.params.{ClientPNames, CookiePolicy}
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import slack.rtm.RtmState

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object Jenkins {
  def propsOption(state: RtmState, name: String): Option[Props] = {
    val nameUpper = name.toUpperCase
    for (url <- sys.env.get(s"${nameUpper}_URL");
         user <- sys.env.get(s"${nameUpper}_USER");
         password <- sys.env.get(s"${nameUpper}_PASSWORD"))
      yield Props(classOf[Jenkins], state, name, url, user, password, sys.env.get(s"${nameUpper}_BUILD_TOKEN"))
  }
}

class Jenkins(state: RtmState,
              val name: String,
              url: String,
              user: String,
              password: String,
              buildToken: Option[String])
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

  private val uri = new URI(url)
  private val rawHttpClient = new DefaultHttpClient()
  rawHttpClient.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY);
  private val cookieClient = new JenkinsHttpClient(uri, rawHttpClient)
  private val basicAuthClient = new JenkinsHttpClient(uri, user, password)
  private val server = new JenkinsServer(basicAuthClient)

  log.info(s"$name plugin has connected to $url")

  private val CacheExpiration = 15000
  private val cacheLock = new AnyRef
  private var cachedJobs: Option[Map[String, Job]] = None
  private var lastCacheTime = 0l

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
              msg.response(unknownJobMessage(jobName))
          }
      } pipeTo sender()

    case TriggerJob(givenName) =>

      val msg = botMessage
      Future[SendSlackMessage] {
        Try(server.getJob(givenName)) match {
          case Success(jobWithDetails) if jobWithDetails != null =>
            val jobName = jobWithDetails.getName
            val isBuildable = jobWithDetails.isBuildable
            if (!isBuildable) {
              msg.response(s"$jobName is not buildable.")
            } else {
              try {
                val encodedJobName = URLEncoder.
                  encode(jobWithDetails.getName, "UTF-8").
                  replaceAll("\\+", "%20")

                log.info(s"Triggering $name job $encodedJobName on $url")
                val triggeredBy = state.users.find(_.id == msg.slackMessage.user).map(_.name).getOrElse("unknown user")
                val channelName = state.channels.find(_.id == msg.slackMessage.channel).map(_.name)
                  .orElse(state.ims.find(_.id == msg.slackMessage.channel).map(_.user)).getOrElse(s"unknown: ${msg.slackMessage.channel}")
                val cause = URLEncoder.encode(s"Triggered via sumobot by $triggeredBy in $channelName", "UTF-8")
                buildToken match {
                  case None =>
                    loginWithCookie()
                    triggerBuildWithCookie(encodedJobName)
                  case Some(tkn) =>
                    basicAuthClient.get(s"/job/$encodedJobName/build?delay=0sec&token=$tkn&cause=$cause")
                }
                cachedJobs = None
                msg.response(s"$name job $jobName has been triggered!")
              } catch {
                case NonFatal(e) =>
                  log.error(e, s"Could not trigger job $jobName")
                  msg.response("Unable to trigger job. Got an exception")
              }
            }
          case Failure(e) =>
            log.error(e, s"Error triggering $name job $givenName on $url")
            msg.response(unknownJobMessage(givenName))
          case _ =>
            msg.response(unknownJobMessage(givenName))
        }
      } pipeTo sender()

    case Info() =>
      botMessage.say(s"$name URL: $url - connected as $user")
      val msg = botMessage
      jobs.map {
        jobsMap =>
          msg.response(s"${jobsMap.size} jobs known on $name")
      } pipeTo sender()
  }

  private def jobs: Future[Map[String, Job]] = {
    log.info("jobs called")
    Future {
      cacheLock synchronized {
        if (cachedJobs.isEmpty || System.currentTimeMillis() - lastCacheTime > CacheExpiration) {
          log.debug(s"Loading jobs from $url")
          cachedJobs = Some(server.getJobs.asScala.toMap)
          lastCacheTime = System.currentTimeMillis()
        }
      }

      cachedJobs.get
    }
  }

  private def loginWithCookie(): Unit = {
    val request = new HttpPost(url + "j_acegi_security_check")
    val pairs = List(
      new BasicNameValuePair("j_username", user),
      new BasicNameValuePair("j_password", password),
      new BasicNameValuePair("remember_me", "on"),
      new BasicNameValuePair("from", "/"),
      new BasicNameValuePair("submit", "log+in")
    )
    request.setEntity(new UrlEncodedFormEntity(pairs.asJava, "UTF-8"))
    val response = rawHttpClient.execute(request)
    try {
      val status = response.getStatusLine.getStatusCode
      val responseText = EntityUtils.toString(response.getEntity)
      require(status > 200 && status < 400, s"Returned error $status ($responseText)")
    } finally {
      EntityUtils.consume(response.getEntity)
      request.releaseConnection()
    }
  }

  private def triggerBuildWithCookie(jobName: String): Unit = {
    val post = new HttpPost(url + s"/job/$jobName/build?delay=0sec")
    val response = rawHttpClient.execute(post)
    try {
      val status = response.getStatusLine.getStatusCode
      val responseText = EntityUtils.toString(response.getEntity)
      require(status > 200 && status < 400, s"Returned error $status ($responseText)")
    } finally {
      EntityUtils.consume(response.getEntity)
      post.releaseConnection()
    }
  }

  private def unknownJobMessage(jobName: String) = chooseRandom(
    s"I don't know any job named $jobName!! $upset",
    s"Bite my shiny metal ass. There's no job named $jobName!"
  )
}

