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

import akka.actor.ActorRef
import slack.models.{ActionField => SActionField, Attachment => SAttachment, AttachmentField => SAttachmentField, ConfirmField => SConfirmField}

import java.io.File

case class OutgoingMessage(channel: Channel, text: String, threadTs: Option[String] = None)

// NOTE(mccartney, 2018-11-02): Slack API doesn't allow sending messages with attachments using the RTM client,
// thus modelling it as a separate case class. Although the document structure is consistent with `OutgoingMessage`.
// See https://api.slack.com/rtm#formatting_messages
case class OutgoingMessageWithAttachments(channel: Channel, text: String,
                                          threadTs: Option[String], attachments: Seq[Attachment] = Seq())

case class Attachment(fallback: Option[String] = None,
                      callbackId: Option[String] = None,
                      color: Option[String] = None,
                      pretext: Option[String] = None,
                      authorName: Option[String] = None,
                      authorLink: Option[String] = None,
                      authorIcon: Option[String] = None,
                      title: Option[String] = None,
                      titleLink: Option[String] = None,
                      text: Option[String] = None,
                      fields: Seq[AttachmentField] = Seq.empty,
                      imageUrl: Option[String] = None,
                      thumbUrl: Option[String] = None,
                      actions: Seq[ActionField] = Seq.empty,
                      mrkdwnIn: Seq[String] = Seq.empty)
case class AttachmentField(title: String, value: String, short: Boolean)

case class ActionField(name: String,
                       text: String,
                       actionType: String,
                       style: Option[String] = None,
                       value: Option[String] = None,
                       confirm: Option[ConfirmField] = None)

case class ConfirmField(text: String,
                        title: Option[String] = None,
                        okText: Option[String] = None,
                        cancelText: Option[String] = None)

case class OpenIM(userId: String, doneRecipient: ActorRef, doneMessage: AnyRef)

case class IncomingMessage(canonicalText: String,
                           addressedToUs: Boolean,
                           channel: Channel,
                           idTimestamp: String,
                           threadTimestamp: Option[String] = None,
                           attachments: Seq[IncomingMessageAttachment] = Seq(),
                           sentBy: Sender)

case class IncomingMessageAttachment(text: String, title: String)

case class OutgoingImage(channel: Channel, image: File, contentType: String, title: String,
                         comment: Option[String] = None, threadTimestamp: Option[String] = None)


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

case class ResponseInProgress(channel: Channel)

object PublicHttpsReference {
  def forMessage(baseSlackUrl: String, msg: IncomingMessage) = {
    val clearId = msg.idTimestamp.replace(".", "")
    val channelId = msg.channel.id
    s"$baseSlackUrl/archives/$channelId/p$clearId"
  }
}

object Messages {

  def convertToSlackModel(attachments: Seq[Attachment]): Option[Seq[SAttachment]] = {
    Some(
      attachments.map {
        a =>
          SAttachment(fallback = a.fallback,
            callback_id = a.callbackId,
            color = a.color,
            pretext = a.pretext,
            author_name = a.authorName,
            author_link = a.authorLink,
            title = a.title,
            title_link = a.titleLink,
            text = a.text,
            fields = convertFieldsToSlackModel(a.fields),
            image_url = a.imageUrl,
            thumb_url = a.thumbUrl,
            actions = convertActionsToSlackModel(a.actions),
            mrkdwn_in = Some(a.mrkdwnIn))
      }
    )
  }

  private def convertFieldsToSlackModel(fields: Seq[AttachmentField]): Option[Seq[SAttachmentField]] = {
    Some(fields.map {
      f =>
        SAttachmentField(title = f.title, value = f.value, short = f.short)
    })
  }

  private def convertActionsToSlackModel(actions: Seq[ActionField]): Option[Seq[SActionField]] = {
    Some(actions.map {
      a =>
        SActionField(name = a.name, text = a.text, `type` = a.actionType, style = a.style, value = a.value,
          confirm = a.confirm.map(convertConfirmFieldToSlackModel))
    })
  }

  private def convertConfirmFieldToSlackModel(confirm: ConfirmField): SConfirmField = {
      SConfirmField(text = confirm.text, title = confirm.title, ok_text = confirm.okText, cancel_text = confirm.cancelText)
  }
}
