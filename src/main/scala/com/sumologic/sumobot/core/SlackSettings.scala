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

import akka.actor.ActorSystem
import com.netflix.config.scala.{DynamicIntProperty, DynamicStringProperty}
import slack.rtm.SlackRtmClient


object SlackSettings {
  val ApiToken = DynamicStringProperty("slack.api.token", null)

  // TODO: Wire this up when the 0.1.1 version of slack-scala-client is released.
  val ConnectTimeout = DynamicIntProperty("slack.connect.timeout.seconds", 15)

  def connectOrExit(implicit system: ActorSystem): SlackRtmClient = {
    ApiToken() match {
      case Some(token) =>
        SlackRtmClient(token)
      case None =>
        println(s"Please set the slack.api.token environment variable!")
        sys.exit(1)
        null
    }
  }
}
