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

import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef, Props}
import com.sumologic.sumobot.core.model.PublicChannel
import com.sumologic.sumobot.plugins.BotPlugin.{InitializePlugin, PluginAdded, PluginRemoved}
import slack.api.RtmConnectState
import slack.models.{Channel, Team, User}
import slack.rtm.RtmState

import java.time.Instant

object HttpReceptionist {
  private[core] val DefaultChannel = Channel("C0001SUMO", Some("sumobot"), Instant.now().getEpochSecond(),
    Some("U0001SUMO"), Some(false), Some(true), Some(false), Some(false), Some(true), None, Some(false), Some(false), None, None, None, None, None, None, None, None)
  val DefaultSumoBotChannelId = DefaultChannel.id
  val DefaultSumoBotChannel = PublicChannel(DefaultChannel.id, DefaultChannel.name.get)

  val DefaultBotUser = User("U0001SUMO", "sumobot-bot", None, None, None, None, None, None, None, None, None, None, None, None, None, None)
  val DefaultClientUser = User("U0002SUMO", "sumobot-client", None, None, None, None, None, None, None, None, None, None, None, None, None, None)

  private[core] val StateUrl = ""
  private[core] val StateTeam = Team("T0001SUMO", "Sumo Bot", "sumobot")

  private[core] val StartState = RtmConnectState(true, StateUrl, DefaultBotUser, StateTeam)
  private[core] val State = new RtmState(StartState)
}

class HttpReceptionist(brain: ActorRef) extends Actor with ActorLogging {
  private val pluginRegistry = context.system.actorOf(Props(classOf[PluginRegistry]), "plugin-registry")

  override def receive: Receive = {
    case message@PluginAdded(plugin, _) =>
      plugin ! InitializePlugin(HttpReceptionist.State, brain, pluginRegistry)
      pluginRegistry ! message

    case message@PluginRemoved(_) =>
      pluginRegistry ! message
  }
}
