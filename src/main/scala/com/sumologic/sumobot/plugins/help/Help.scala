package com.sumologic.sumobot.plugins.help

import akka.actor.ActorRef
import com.sumologic.sumobot.plugins.BotPlugin
import com.sumologic.sumobot.plugins.BotPlugin.RequestHelp
import com.sumologic.sumobot.plugins.help.Help.{PluginAdded, AddHelp}

object Help {
  case class PluginAdded(plugin: ActorRef)
  case class AddHelp(pluginName: String, helpText: String)
}

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

  private def helpReceive: Receive = {
    case AddHelp(pluginName, text) =>
      helpText = helpText + (pluginName -> text)

    case PluginAdded(plugin) =>
      plugin ! RequestHelp
  }

  private val ListPlugins = matchText("help")
  private val HelpForPlugin = matchText("help (\\w+).*")

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
