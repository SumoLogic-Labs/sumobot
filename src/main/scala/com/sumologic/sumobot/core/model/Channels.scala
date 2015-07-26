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

