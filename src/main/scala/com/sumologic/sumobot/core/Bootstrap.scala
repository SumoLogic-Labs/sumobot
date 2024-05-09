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

import org.apache.pekko.actor.{ActorRef, ActorSystem, Props}
import com.sumologic.sumobot.http_frontend.{SumoBotHttpServer, SumoBotHttpServerOptions}
import com.sumologic.sumobot.plugins.PluginCollection
import com.sumologic.sumobot.util.ParExecutor
import com.typesafe.config.{Config, ConfigFactory}
import slack.api.{BlockingSlackApiClient, SlackApiClient}
import slack.rtm.SlackRtmClient

import java.io.File
import scala.concurrent.Await
import scala.concurrent.duration._

object Bootstrap {
  sealed trait SumobotFrontend
  case object SlackFrontend extends SumobotFrontend
  case object HttpFrontend extends SumobotFrontend

  private val configFileLocation = "config/sumobot.conf"

  // not sealed, implement own if you need something else
  trait ConfigReader {
    def readConfig: Config
  }
  case object FileConfigReader extends ConfigReader {
    override def readConfig: Config =
      ConfigFactory
        .parseFile(new File(configFileLocation))
        .resolve()
        .withFallback(ConfigFactory.systemEnvironmentOverrides())
  }
  case object ClasspathConfigReader extends ConfigReader {
    override def readConfig: Config = ConfigFactory.load(configFileLocation)
  }

  implicit var system: ActorSystem = _

  var receptionist: Option[ActorRef] = None

  def bootstrap(brainProps: Props, configReader: ConfigReader, pluginCollections: Seq[PluginCollection]): Unit = {
    system = ActorSystem("sumobot", configReader.readConfig)
    val frontend = selectedFrontend()

    frontend match {
      case SlackFrontend =>
        bootstrapSlack(brainProps, pluginCollections)
      case HttpFrontend =>
        bootstrapHttp(brainProps, pluginCollections)
    }
  }

  def bootstrap(brainProps: Props, configReader: ConfigReader, pluginCollection: PluginCollection): Unit = {
    bootstrap(brainProps, configReader, Seq(pluginCollection))
  }

  def bootstrap(brainProps: Props, pluginCollections: PluginCollection*): Unit = {
    bootstrap(brainProps, FileConfigReader, pluginCollections)
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

    ParExecutor.runInParallel(pluginCollections)(_.setup)

    sys.addShutdownHook(shutdownActorSystem())
  }

  private def bootstrapHttp(brainProps: Props, pluginCollections: Seq[PluginCollection]): Unit = {
    val httpConfig = system.settings.config.getConfig("http")

    val brain = system.actorOf(brainProps, "brain")
    val httpServerOptions = SumoBotHttpServerOptions.fromConfig(httpConfig)
    val httpServer = new SumoBotHttpServer(httpServerOptions)

    receptionist = Some(system.actorOf(Props(classOf[HttpReceptionist], brain), "receptionist"))

    ParExecutor.runInParallel(pluginCollections)(_.setup)

    sys.addShutdownHook(httpServer.terminate())
    sys.addShutdownHook(shutdownActorSystem())
  }

  private def selectedFrontend(): SumobotFrontend = {
    val isSlackSelected =
      system.settings.config.hasPath("slack.api.token") && isFrontendEnabled("slack")
    val isHttpSelected = system.settings.config.hasPath("http") && isFrontendEnabled("http")

    if (isHttpSelected && isSlackSelected) throw new IllegalArgumentException("Only one frontend can be selected")
    if (!isHttpSelected && !isSlackSelected) throw new IllegalArgumentException("No frontend selected")

    if (isSlackSelected) SlackFrontend else HttpFrontend
  }

  private def isFrontendEnabled(frontendName: String): Boolean = {
    !system.settings.config.hasPath(s"$frontendName.enabled") ||
      system.settings.config.getBoolean(s"$frontendName.enabled")
  }

  private def shutdownActorSystem(): Unit = {
    Await.result(system.terminate(), Duration.Inf)
  }

  def shutdown(): Unit = {
    shutdownActorSystem()
    sys.exit()
  }
}
