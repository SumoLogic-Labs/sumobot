package com.sumologic.sumobot.core

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.sumologic.sumobot.core.PluginRegistry.{RequestPluginList, Plugin, PluginList}
import com.sumologic.sumobot.plugins.BotPlugin.{PluginAdded, PluginRemoved}

object PluginRegistry {

  case class Plugin(plugin: ActorRef, help: String)

  case object RequestPluginList
  case class PluginList(plugins: Seq[Plugin])
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
  }
}
