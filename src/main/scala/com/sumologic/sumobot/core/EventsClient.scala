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

package com.sumologic.sumobot.core

import akka.actor.ActorRef
import com.slack.api.bolt.handler.BoltEventHandler
import com.slack.api.bolt.{App, AppConfig}
import com.slack.api.bolt.socket_mode.SocketModeApp
import com.slack.api.model.event.MessageEvent
import com.slack.api.socket_mode.SocketModeClient
import org.slf4j.LoggerFactory
import slack.models.Message
import scala.jdk.CollectionConverters._

object EventsClient {
  def apply(appToken: String, botToken: String): EventsClient = {
    val appConfig = AppConfig.builder.singleTeamBotToken(botToken).build
    new EventsClient(appToken, appConfig)
  }
}

class EventsClient private (appToken: String, appConfig: AppConfig) {
  private val log = LoggerFactory.getLogger(getClass)
  private val app = new App(appConfig)
  private val socketModeApp = new SocketModeApp(appToken, app)
  @volatile private var clientOpt: Option[SocketModeClient] = None

  def addEventListener(messageRouter: ActorRef): Unit = {
    val messageEventHandler: BoltEventHandler[MessageEvent] = (payload, context) => {
      val event = payload.getEvent
      if (event.getText != null && event.getUser != null) {
        val incoming = Message(
          event.getTs,
          event.getChannel,
          Option(event.getUser),
          event.getText,
          Option(event.getBotId),
          None,
          Option(event.getThreadTs),
          Option(Option(event.getAttachments)
            .map(_.asScala.toSeq).getOrElse(Seq.empty)
            .asInstanceOf[Seq[slack.models.Attachment]]),
          Option(event.getSubtype)
        )
        messageRouter ! incoming
      }
      context.ack()
    }

    app.event(classOf[MessageEvent], messageEventHandler)

    socketModeApp.startAsync()
    val client = socketModeApp.getClient
    clientOpt = Some(client)

    client.addWebSocketMessageListener((message: String) => {
      if (message.contains("disconnect")) {
        log.warn("Slack SocketMode disconnected — reconnecting...")
        tryReconnect()
      }
    })
  }

  private def tryReconnect(): Unit = {
    clientOpt.foreach { client =>
      try {
        client.disconnect()
        socketModeApp.startAsync()
        log.info("Slack SocketMode reconnected.")
        val newClient = socketModeApp.getClient
        clientOpt = Some(newClient)
        newClient.addWebSocketMessageListener((message: String) => {
          if (message.contains("disconnect")) {
            log.warn("Slack SocketMode disconnected — reconnecting...")
            tryReconnect()
          }
        })
      } catch {
        case e: Exception =>
          log.error("Failed to reconnect SocketMode", e)
      }
    }
  }

  def destroy(): Unit = {
    if(app != null) {
      app.stop()
    }
    if(socketModeApp != null) {
      socketModeApp.stop()
    }
  }
}