package com.sumologic.sumobot.core

import slack.models.{Message, User}
import slack.rtm.RtmState

object Channel {


  def forMessage(state: RtmState, slackMessage: Message): Channel = forChannelId(state, slackMessage.channel)

  def forChannelId(state: RtmState, channelId: String): Channel = {

    def publicChannel: Option[PublicChannel] =
      state.channels.find(_.id == channelId).map(ch => PublicChannel(ch.id, ch.name))

    def groupChannel: Option[GroupChannel] =
      state.groups.find(_.id == channelId).map(ch => GroupChannel(ch.id, ch.name))

    def imChannel: Option[InstantMessageChannel] =
      for (im <- state.ims.find(_.id == channelId);
           user <- state.users.find(_.id == im.user))
        yield InstantMessageChannel(im.id, user)

    (imChannel orElse groupChannel orElse publicChannel).
      getOrElse(throw new IllegalArgumentException(s"Unknown channel: $channelId"))
  }
}

trait Channel {
  def id: String

  def name: String
}

case class PublicChannel(id: String, name: String) extends Channel

case class GroupChannel(id: String, name: String) extends Channel

case class InstantMessageChannel(id: String, user: User) extends Channel {
  override def name: String = user.name
}

