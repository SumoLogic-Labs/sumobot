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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.sumologic.sumobot.core.model.PublicChannel
import com.sumologic.sumobot.plugins.BotPlugin.{InitializePlugin, PluginAdded, PluginRemoved}
import slack.models.{Channel, User}

import java.time.Instant

object HttpReceptionist {
  private[core] val DefaultChannel = Channel("C0001SUMO", "sumobot", Instant.now().getEpochSecond(),
    Some("U0001SUMO"), Some(false), Some(true), Some(false), Some(false), Some(true), None, Some(false), Some(false), None, None, None, None, None, None, None, None)
  val DefaultSumoBotChannelId = DefaultChannel.id
  val DefaultSumoBotChannel = PublicChannel(DefaultChannel.id, DefaultChannel.name)

  val DefaultClientUser = User("U0002SUMO", "sumobot-client", None, None, None, None, None, None, None, None, None, None, None, None, None, None)
}

class HttpReceptionist(brain: ActorRef) extends Actor with ActorLogging {
  private val pluginRegistry = context.system.actorOf(Props(classOf[PluginRegistry]), "plugin-registry")

  override def receive: Receive = {
    case message@PluginAdded(plugin, _) =>
      plugin ! InitializePlugin(brain, pluginRegistry)
      pluginRegistry ! message

    case message@PluginRemoved(_) =>
      pluginRegistry ! message
  }
}
