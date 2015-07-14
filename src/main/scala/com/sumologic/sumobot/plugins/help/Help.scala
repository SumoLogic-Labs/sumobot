package com.sumologic.sumobot.plugins.help

import com.sumologic.sumobot.plugins.BotPlugin
import com.sumologic.sumobot.plugins.BotPlugin.PluginAdded


class Help extends BotPlugin {
  override protected def name = "help"

  override protected def help =
    s"""I can help you understand plugins.
       |
       |help - I'll tell you what plugins I've got.
       |help <plugin>. - I'll tell you how <plugin> works.
     """.stripMargin

  private var helpText = Map[String, String]().empty

  override def receive = super.receive orElse helpReceive

  override def preStart(): Unit = {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[PluginAdded])
  }

  override def postStop(): Unit = {
    super.postStop()
    context.system.eventStream.unsubscribe(self)
  }

  private def helpReceive: Receive = {
    case PluginAdded(_, pluginName, text) =>
      helpText = helpText + (pluginName -> text)
  }

  private val ListPlugins = matchText("help")
  private val HelpForPlugin = matchText("help ([\\-\\w]+).*")

  override protected def receiveText = {
    case ListPlugins() if botMessage.addressedToUs =>
      botMessage.say(helpText.keys.toList.sorted.mkString("\n"))

    case HelpForPlugin(pluginName) if botMessage.addressedToUs =>
      helpText.get(pluginName) match {
        case Some(text) =>
          botMessage.say(text)
        case None =>
          botMessage.respond(s"Sorry, I don't know $pluginName")
      }
  }
}
