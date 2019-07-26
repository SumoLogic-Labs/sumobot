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

import com.sumologic.sumobot.http_frontend.authentication.{BasicAuthentication, HttpAuthentication, NoAuthentication}
import com.typesafe.config.Config

case class SumoBotHttpServerOptions(httpHost: String, httpPort: Int,
                                    origin: String, authentication: HttpAuthentication)

sealed trait AuthenticationConfig {
  def configKey: String
}

case object NoAuthenticationConfig extends AuthenticationConfig {
  override val configKey: String = "no-authentication"
}

case object BasicAuthenticationConfig extends AuthenticationConfig {
  override val configKey: String = "basic-authentication"
}

object SumoBotHttpServerOptions {
  private val AuthenticationConfigs = Array(NoAuthenticationConfig, BasicAuthenticationConfig)

  def fromConfig(config: Config): SumoBotHttpServerOptions = {
    val httpHost = config.getString("host")
    val httpPort = config.getInt("port")
    val origin = if (config.hasPath("origin")) {
      config.getString("origin")
    } else SumoBotHttpServer.DefaultOrigin

    val authentication = authenticationFromConfig(config)

    SumoBotHttpServerOptions(httpHost, httpPort, origin, authentication)
  }

  private def authenticationFromConfig(config: Config): HttpAuthentication = {
    selectedAuthentication(config) match {
      case Some(NoAuthenticationConfig) =>
        new NoAuthentication()

      case Some(BasicAuthenticationConfig) =>
        val username = config.getString(s"${BasicAuthenticationConfig.configKey}.username")
        val password = config.getString(s"${BasicAuthenticationConfig.configKey}.password")

        new BasicAuthentication(username, password)

      case None =>
        SumoBotHttpServer.DefaultAuthentication
    }
  }

  private def selectedAuthentication(config: Config): Option[AuthenticationConfig] = {
    val selectedAuthConfigs = AuthenticationConfigs.filter(authConfig => config.hasPath(authConfig.configKey))

    if (selectedAuthConfigs.length > 1) throw new IllegalArgumentException("Only one authentication method can be selected")

    selectedAuthConfigs.headOption
  }
}