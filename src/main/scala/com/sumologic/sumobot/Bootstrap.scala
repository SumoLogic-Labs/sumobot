package com.sumologic.sumobot

import akka.actor.{ActorSystem, Props}
import com.sumologic.sumobot.plugins.PluginCollection

object Bootstrap {
  def bootstrap(brainProps: Props, pluginCollections: PluginCollection*): Unit = {
    implicit val system = ActorSystem("root")
    val rtmClient = SlackSettings.connectOrExit
    val brain = system.actorOf(brainProps, "brain")
    system.actorOf(Receptionist.props(rtmClient, brain), "bot")

    pluginCollections.par.foreach(_.setup)

    def shutdownAndWait: Unit = {
      system.shutdown()
      system.awaitTermination()
    }

    sys.addShutdownHook(shutdownAndWait)
  }
}
