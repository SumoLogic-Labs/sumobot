package com.sumologic.sumobot.plugins

import akka.actor.{ActorSystem, Props}

trait PluginCollection {
  protected def addPlugin(name: String, props: Props)(implicit system: ActorSystem) = system.actorOf(props, name)

  def setup(implicit system: ActorSystem): Unit
}
