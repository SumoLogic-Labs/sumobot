package com.sumologic.sumobot.core

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.sumologic.sumobot.core.PluginRegistry.{RequestPluginList, Plugin, PluginList}
import com.sumologic.sumobot.plugins.BotPlugin.{PluginAdded, PluginRemoved}

object PluginRegistry {

  case class Plugin(name: String, help: String, actor: ActorRef)

  case object RequestPluginList
  case class PluginList(plugins: Seq[Plugin])
}

class PluginRegistry extends Actor with ActorLogging {

  private var list = List.empty[Plugin]

  override def receive: Receive = {
    case PluginAdded(plugin, name, help) =>
      log.info(s"Plugin added: $name")
      if (list.exists(_.name == name)) {
        log.error(s"Attempt to register duplicate plugin: $name")
      } else {
        list +:= Plugin(name, help, plugin)
      }

    case PluginRemoved(_, name) =>
      list = list.filterNot(_.name == name)
      log.info(s"Plugin removed: $name")

    case RequestPluginList =>
      sender() ! PluginList(list)
  }
}
