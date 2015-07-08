package com.sumologic.sumobot.plugins.jenkins

import java.net.{URI, URLEncoder}

import com.offbytwo.jenkins.JenkinsServer
import com.offbytwo.jenkins.client.JenkinsHttpClient
import com.offbytwo.jenkins.model.Job
import com.sumologic.sumobot.plugins.Emotions
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.params.{ClientPNames, CookiePolicy}
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object JenkinsJobClient {
  def createClient(name: String): Option[JenkinsJobClient] = {
    val nameUpper = name.toUpperCase
    for (url <- sys.env.get(s"${nameUpper}_URL");
         user <- sys.env.get(s"${nameUpper}_USER");
         password <- sys.env.get(s"${nameUpper}_PASSWORD"))
      yield new JenkinsJobClient(name, url, user, password, sys.env.get(s"${nameUpper}_BUILD_TOKEN"))
  }
}

class JenkinsJobClient(val name: String,
                       url: String,
                       user: String,
                       password: String,
                       buildToken: Option[String])
  extends Emotions {

  private val log = LoggerFactory.getLogger(classOf[JenkinsJobClient])

  private val uri = new URI(url)
  private val rawHttpClient = new DefaultHttpClient()
  rawHttpClient.getParams.setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY)

  private val basicAuthClient = new JenkinsHttpClient(uri, user, password)

  private val server = new JenkinsServer(basicAuthClient)

  private val CacheExpiration = 15000
  private val cacheLock = new AnyRef
  private var cachedJobs: Option[Map[String, Job]] = None
  private var lastCacheTime = 0l

  def triggerJob(givenName: String, cause: String): String = {
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
              log.error(s"Could not trigger job $jobName", e)
              "Unable to trigger job. Got an exception"
          }
        }
      case Failure(e) =>
        log.error(s"Error triggering job $givenName on $url", e)
        unknownJobMessage(givenName)
      case _ =>
        unknownJobMessage(givenName)
    }
  }

  def jobs: Map[String, Job] = {
    cacheLock synchronized {
      if (cachedJobs.isEmpty || System.currentTimeMillis() - lastCacheTime > CacheExpiration) {
        cachedJobs = Some(server.getJobs.asScala.toMap)
        lastCacheTime = System.currentTimeMillis()
      }
    }

    cachedJobs.get
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


  def unknownJobMessage(jobName: String) = chooseRandom(
    s"I don't know any job named $jobName!! $upset",
    s"Bite my shiny metal ass. There's no job named $jobName!"
  )
}
