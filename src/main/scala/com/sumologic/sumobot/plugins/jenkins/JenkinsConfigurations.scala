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

import com.sumologic.sumobot.core.config.ListOfConfigs
import com.typesafe.config.Config

import scala.util.Try

case class JenkinsConfiguration(name: String,
                                url: String,
                                username: String,
                                password: String,
                                buildToken: Option[String])

object JenkinsConfigurations {
  def load(config: Config): Map[String, JenkinsConfiguration] = {
    ListOfConfigs.parse(config, "plugins.jenkins.connections") {
      (name, connectionConfig) =>
        val url = connectionConfig.getString("url")
        val username = connectionConfig.getString("username")
        val password = connectionConfig.getString("password")
        val buildToken = Try(connectionConfig.getString("build.token")).toOption
        JenkinsConfiguration(name, url, username, password, buildToken)         
    }
  }
}
