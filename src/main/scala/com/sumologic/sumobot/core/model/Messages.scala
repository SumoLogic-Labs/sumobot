/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sumologic.sumobot.core.model

import java.io.File

import akka.actor.ActorRef
import slack.models.Attachment

case class OutgoingMessage(channel: Channel, text: String, threadTs: Option[String] = None)

// NOTE(mccartney, 2018-11-02): Slack API doesn't allow sending messages with attachments using the RTM client,
// thus modelling it as a separate case class. Although the document structure is consistent with `OutgoingMessage`.
// See https://api.slack.com/rtm#formatting_messages
case class OutgoingMessageWithAttachments(channel: Channel, text: String,
                                          threadTs: Option[String], attachments: Seq[Attachment] = Seq())

case class OpenIM(userId: String, doneRecipient: ActorRef, doneMessage: AnyRef)

case class IncomingMessage(canonicalText: String,
                           addressedToUs: Boolean,
                           channel: Channel,
                           idTimestamp: String,
                           threadTimestamp: Option[String] = None,
                           attachments: Seq[IncomingMessageAttachment] = Seq(),
                           sentBy: Sender)

case class IncomingMessageAttachment(text: String)

case class OutgoingImage(channel: Channel, image: File, contentType: String, title: String,
                         comment: Option[String] = None)


sealed abstract class Sender {
  def slackReference: String
  def plainTextReference: String
}

case class UserSender(slackUser: slack.models.User) extends Sender {
  override def slackReference: String = s"<@${slackUser.id}>"
  override def plainTextReference: String = slackUser.id
}
case class BotSender(id: String) extends Sender {
  override def slackReference: String = s"app: $id"
  override def plainTextReference: String = slackReference
}

object PublicHttpsReference {
  def forMessage(baseSlackUrl: String, msg: IncomingMessage) = {
    val clearId = msg.idTimestamp.replace(".", "")
    val channelId = msg.channel.id
    s"$baseSlackUrl/archives/$channelId/p$clearId"
  }
}
