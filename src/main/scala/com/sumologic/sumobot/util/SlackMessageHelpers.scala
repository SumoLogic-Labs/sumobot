package com.sumologic.sumobot.util

import slack.models.Message
import slack.rtm.RtmState

object SlackMessageHelpers {
  def isInstantMessage(slackMessage: Message)(implicit state: RtmState): Boolean = state.ims.exists(_.id == slackMessage.channel)
}
