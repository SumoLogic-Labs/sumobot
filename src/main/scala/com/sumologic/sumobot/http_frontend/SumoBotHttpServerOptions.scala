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
package com.sumologic.sumobot.http_frontend

import com.sumologic.sumobot.http_frontend.authentication.{HttpAuthentication, Link, NoAuthentication}
import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters._
import scala.util.{Success, Try}

case class SumoBotHttpServerOptions(httpHost: String, httpPort: Int,
                                    origin: String, authentication: HttpAuthentication,
                                    title: String, description: Option[String],
                                    links: Seq[Link])

object SumoBotHttpServerOptions {
  val DefaultOrigin = "*"
  val DefaultAuthentication = new NoAuthentication(ConfigFactory.empty())
  val DefaultTitle = "Sumobot-over-HTTP"

  def fromConfig(config: Config): SumoBotHttpServerOptions = {
    val httpHost = config.getString("host")
    val httpPort = config.getInt("port")
    val origin = if (config.hasPath("origin")) {
      config.getString("origin")
    } else DefaultOrigin

    val authentication = authenticationFromConfig(config)

    val title = if (config.hasPath("title")) {
      config.getString("title")
    } else DefaultTitle

    val description = if (config.hasPath("description")) {
      Some(config.getString("description"))
    } else None

    val links = linksFromConfig(config)

    SumoBotHttpServerOptions(httpHost, httpPort, origin,
      authentication, title, description, links)
  }

  private def authenticationFromConfig(config: Config): HttpAuthentication = {
    Try(config.getConfig("authentication")) match {
      case Success(authenticationConfig)
      if authenticationConfig.getBoolean("enabled") =>
        val clazz = Class.forName(authenticationConfig.getString("class"))
        val constructor = clazz.getConstructor(classOf[Config])
        constructor.newInstance(authenticationConfig).asInstanceOf[HttpAuthentication]

      case _ => DefaultAuthentication
    }
  }

  private def linksFromConfig(config: Config): Seq[Link] = {
    Try(config.getObject("links").asScala) match {
      case Success(links) =>
        links.map {
          case (link, _) =>
            val linkName = config.getString(s"links.$link.name")
            val linkHref = config.getString(s"links.$link.href")
            Link(linkName, linkHref)
        }.toSeq
      case _ => Seq.empty
    }
  }
}
