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

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.sumologic.sumobot.core.PluginRegistry.{EnsureRequiredPlugins, RequestPluginList, Plugin, PluginList}
import com.sumologic.sumobot.plugins.BotPlugin.{PluginAdded, PluginRemoved}
import collection.JavaConverters._

import scala.util.Try

object PluginRegistry {

  case class Plugin(plugin: ActorRef, help: String)

  case object RequestPluginList
  case class PluginList(plugins: Seq[Plugin])

  case object EnsureRequiredPlugins
}

class PluginRegistry extends Actor with ActorLogging {

  private var list = List.empty[Plugin]

  override def receive: Receive = {
    case PluginAdded(plugin, help) =>
      val name = plugin.path.name
      log.info(s"Plugin added: $name")
      if (list.exists(_.plugin.path.name == name)) {
        log.error(s"Attempt to register duplicate plugin: $name")
      } else {
        list +:= Plugin(plugin, help)
      }

    case PluginRemoved(plugin) =>
      val name = plugin.path.name
      list = list.filterNot(_.plugin.path.name == name)
      log.info(s"Plugin removed: $name")

    case RequestPluginList =>
      sender() ! PluginList(list)

    case EnsureRequiredPlugins =>
      val requiredPlugins: Seq[String] = Try(context.system.settings.config.getStringList("plugins.required").asScala).
        toOption.getOrElse(List.empty)
      val missing = requiredPlugins.filterNot(r => list.exists(_.plugin.path.name == r))
      if (missing.nonEmpty) {
        log.error(s"REQUIRED PLUGINS MISSING:\n${missing.sorted.mkString("\n")}")
        context.system.shutdown()
      } else {
        log.info("All required plugins have been registered.")
      }
  }
}
