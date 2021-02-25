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

import akka.event.Logging
import com.offbytwo.jenkins.JenkinsServer
import com.offbytwo.jenkins.client.JenkinsHttpClient
import com.offbytwo.jenkins.model.Job
import com.sumologic.sumobot.core.Bootstrap
import com.sumologic.sumobot.core.util.TimeHelpers
import com.sumologic.sumobot.plugins.{Emotions, HttpClientWithTimeOut}
import org.apache.http.client.config.{CookieSpecs, RequestConfig}
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils

import java.net.{URI, URLEncoder}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class JenkinsJobClient(val configuration: JenkinsConfiguration)
  extends Emotions
  with TimeHelpers {

  private val log = Logging.getLogger(Bootstrap.system, this)
  import configuration._
  private val uri = new URI(url)

  private val rawConMan = new PoolingHttpClientConnectionManager()
  rawConMan.setMaxTotal(200)
  rawConMan.setDefaultMaxPerRoute(20)

  val requestConfig = RequestConfig.custom.setCookieSpec(CookieSpecs.BROWSER_COMPATIBILITY)
    .setConnectionRequestTimeout(60000)
    .setConnectTimeout(60000)
    .build

  private val rawHttpClient = HttpClientWithTimeOut.client(requestConfig)

  private val basicAuthClient = new JenkinsHttpClient(uri, username, password)

  private val server = new JenkinsServer(basicAuthClient)

  private val CacheExpiration = Duration(Bootstrap.system.settings.config.getInt(s"plugins.jenkins.cache.expiration.seconds"), TimeUnit.SECONDS)
  private val cacheLock = new AnyRef
  private var cachedJobs: Option[Map[String, Job]] = None
  private var lastCacheTime = 0L

  def buildJob(givenName: String, cause: String): String = {
    Try(server.getJob(givenName)) match {
      case Success(jobWithDetails) if jobWithDetails != null =>
        val jobName = jobWithDetails.getName
        val isBuildable = jobWithDetails.isBuildable
        if (!isBuildable) {
          s"$jobName is not buildable."
        } else {
          try {
            val encodedJobName = URLEncoder.
              encode(jobWithDetails.getName, "UTF-8").
              replaceAll("\\+", "%20")

            log.info(s"Triggering job $encodedJobName on $url")
            buildToken match {
              case None =>
                loginWithCookie()
                triggerBuildWithCookie(encodedJobName)
              case Some(tkn) =>
                basicAuthClient.get(s"/job/$encodedJobName/build?delay=0sec&token=$tkn&cause=$cause")
            }
            cachedJobs = None
            s"job $jobName has been triggered!"
          } catch {
            case NonFatal(e) =>
              log.error(s"Could not trigger job $jobName {}", e)
              "Unable to trigger job. Got an exception"
          }
        }
      case Failure(e) =>
        log.error(s"Error triggering job $givenName on $url {}", e)
        unknownJobMessage(givenName)
      case _ =>
        unknownJobMessage(givenName)
    }
  }

  def jobs: Map[String, Job] = {
    cacheLock synchronized {
      if (cachedJobs.isEmpty || elapsedSince(lastCacheTime) > CacheExpiration) {
        cachedJobs = Some(server.getJobs.asScala.toMap)
        lastCacheTime = now
      }
    }

    cachedJobs.get
  }

  private def loginWithCookie(): Unit = {
    val request = new HttpPost(url + "j_acegi_security_check")
    val pairs = List(
      new BasicNameValuePair("j_username", username),
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

  private[jenkins] def unknownJobMessage(jobName: String) = chooseRandom(
    s"I don't know any job named $jobName!! $upset",
    s"Bite my shiny metal ass. There's no job named $jobName!"
  )
}
