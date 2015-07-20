package com.sumologic.sumobot.core

import akka.actor.ActorRef
import slack.models.Message


case class OutgoingMessage(channelId: String, text: String)

case class OpenIM(userId: String, doneRecipient: ActorRef, doneMessage: AnyRef)

case class IncomingMessage(canonicalText: String,
                           addressedToUs: Boolean,
                           slackMessage: Message) {

  def originalText: String = slackMessage.text
}

