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
package com.sumologic.sumobot.core

import java.io.File

import akka.actor.{ActorRef, ActorSystem, Props}
import com.sumologic.sumobot.http_frontend.authentication.{BasicAuthentication, NoAuthentication}
import com.sumologic.sumobot.http_frontend.{SumoBotHttpServer, SumoBotHttpServerOptions}
import com.sumologic.sumobot.plugins.PluginCollection
import com.typesafe.config.ConfigFactory
import slack.api.{BlockingSlackApiClient, SlackApiClient}
import slack.rtm.SlackRtmClient

import scala.concurrent.Await
import scala.concurrent.duration._

object Bootstrap {
  sealed trait SumobotFrontend
  case object SlackFrontend extends SumobotFrontend
  case object HttpFrontend extends SumobotFrontend

  private val pluginConfig = ConfigFactory.parseFile(new File("config/sumobot.conf"))

  implicit val system = ActorSystem("sumobot", ConfigFactory.load(pluginConfig))

  var receptionist: Option[ActorRef] = None

  def bootstrap(brainProps: Props,
                pluginCollections: PluginCollection*): Unit = {
    val frontend = selectedFrontend()

    frontend match {
      case SlackFrontend =>
        bootstrapSlack(brainProps, pluginCollections)
      case HttpFrontend =>
        bootstrapHttp(brainProps, pluginCollections)
    }
  }

  private def bootstrapSlack(brainProps: Props,
                             pluginCollections: Seq[PluginCollection]): Unit = {
    val slackConfig = system.settings.config.getConfig("slack")
    val rtmClient = SlackRtmClient(
      token = slackConfig.getString("api.token"),
      duration = slackConfig.getInt("connect.timeout.seconds").seconds)

    val syncClient = BlockingSlackApiClient(
      token = slackConfig.getString("api.token"),
      duration = slackConfig.getInt("connect.timeout.seconds").seconds)
    val asyncClient = SlackApiClient(
      token = slackConfig.getString("api.token")
    )
    val brain = system.actorOf(brainProps, "brain")
    receptionist = Some(system.actorOf(Receptionist.props(rtmClient, syncClient, asyncClient, brain), "receptionist"))

    pluginCollections.par.foreach(_.setup)

    sys.addShutdownHook(shutdownActorSystem())
  }

  private def bootstrapHttp(brainProps: Props, pluginCollections: Seq[PluginCollection]): Unit = {
    val httpConfig = system.settings.config.getConfig("http")
    val httpHost = httpConfig.getString("host")
    val httpPort = httpConfig.getInt("port")
    val origin = if (httpConfig.hasPath("origin")) {
      httpConfig.getString("origin")
    } else SumoBotHttpServer.DefaultOrigin

    val brain = system.actorOf(brainProps, "brain")
    val httpServerOptions = SumoBotHttpServerOptions(httpHost, httpPort, origin, new NoAuthentication())
    val httpServer = new SumoBotHttpServer(httpServerOptions)

    receptionist = Some(system.actorOf(Props(classOf[HttpReceptionist], brain), "receptionist"))

    pluginCollections.par.foreach(_.setup)

    sys.addShutdownHook(httpServer.terminate())
    sys.addShutdownHook(shutdownActorSystem())
  }

  private def selectedFrontend(): SumobotFrontend = {
    val isSlackSelected = system.settings.config.hasPath("slack.api.token")
    val isHttpSelected = system.settings.config.hasPath("http")

    if (isHttpSelected && isSlackSelected) throw new IllegalArgumentException("Only one frontend can be selected")
    if (!isHttpSelected && !isSlackSelected) throw new IllegalArgumentException("No frontend selected")

    if (isSlackSelected) SlackFrontend else HttpFrontend
  }

  private def shutdownActorSystem(): Unit = {
    Await.result(system.terminate(), Duration.Inf)
  }

  def shutdown(): Unit = {
    shutdownActorSystem()
    sys.exit()
  }
}
