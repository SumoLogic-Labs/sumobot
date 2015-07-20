package com.sumologic.sumobot.core

import akka.actor.ActorRef
import slack.models.Message


case class OutgoingMessage(channelId: String, text: String)

case class OpenIM(userId: String, doneRecipient: ActorRef, doneMessage: AnyRef)

case class IncomingMessage(canonicalText: String,
                           isAtMention: Boolean,
                           isInstantMessage: Boolean,
                           slackMessage: Message) {

  val addressedToUs: Boolean = isAtMention || isInstantMessage

  def originalText: String = slackMessage.text
}

