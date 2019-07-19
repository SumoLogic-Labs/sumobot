package com.sumologic.sumobot.core

import java.time.Instant

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.sumologic.sumobot.core.model.PublicChannel
import com.sumologic.sumobot.plugins.BotPlugin.{InitializePlugin, PluginAdded, PluginRemoved}
import play.api.libs.json.{JsObject, JsValue}
import slack.api.RtmStartState
import slack.models.{Channel, Group, Im, Team, User}
import slack.rtm.RtmState

object HttpReceptionist {
  private[core] val DefaultChannel = Channel("C0001SUMO", "sumobot", Instant.now().getEpochSecond(),
    Some("U0001SUMO"), Some(false), Some(true), Some(false), Some(true), None, Some(false), None, None, None, None, None, None, None, None)
  val DefaultSumoBotChannel = PublicChannel(DefaultChannel.id, DefaultChannel.name)

  val DefaultBotUser = User("U0001SUMO", "sumobot-bot", None, None, None, None, None, None, None, None, None, None, None, None, None, None)
  val DefaultClientUser = User("U0002SUMO", "sumobot-client", None, None, None, None, None, None, None, None, None, None, None, None, None, None)

  private[core] val StateUrl = ""
  private[core] val StateTeam = Team("T0001SUMO", "Sumo Bot", "sumobot", "sumologic.com", 30, false, new JsObject(Map.empty), "std")
  private[core] val StateUsers: Seq[User] = Array(DefaultBotUser, DefaultClientUser)
  private[core] val StateChannels: Seq[Channel] = Array(DefaultChannel)
  private[core] val StateGroups: Seq[Group] = Seq.empty
  private[core] val StateIms: Seq[Im] = Seq.empty
  private[core] val StateBots: Seq[JsValue] = Seq.empty

  private[core] val StartState = RtmStartState(StateUrl, DefaultBotUser, StateTeam, StateUsers, StateChannels, StateGroups, StateIms, StateBots)
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
