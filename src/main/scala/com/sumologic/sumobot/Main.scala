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
package com.sumologic.sumobot

import akka.actor.ActorSystem
import com.netflix.config.scala.DynamicStringProperty
import slack.rtm.SlackRtmClient

import scala.concurrent.duration._

object Main extends App  {

  private val SlackApiToken = DynamicStringProperty("slack.api.token", null)

  implicit val system = ActorSystem("root")

  SlackApiToken() match {
    case Some(token) =>
      val rtmClient = SlackRtmClient(token, 15.seconds)
      system.actorOf(Receptionist.props(rtmClient), "bot")
      DefaultPlugins.setup()
    case None =>
      println(s"Please set the slack.api.token environment variable!")
      sys.exit(1)
  }
}
