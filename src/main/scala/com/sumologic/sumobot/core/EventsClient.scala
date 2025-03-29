package com.sumologic.sumobot.core

import akka.actor.ActorRef
import com.slack.api.bolt.handler.BoltEventHandler
import slack.models.Message
import com.slack.api.bolt.App
import com.slack.api.bolt.AppConfig
import com.slack.api.bolt.socket_mode.SocketModeApp
import com.slack.api.model.event.MessageEvent
import scala.jdk.CollectionConverters._


object EventsClient {
  def apply(appToken: String, botToken: String): EventsClient = {
    val appConfig = AppConfig.builder.singleTeamBotToken(botToken).build
    new EventsClient(appToken, appConfig)
  }
}

class EventsClient private (appToken: String, appConfig: AppConfig) {
  private val app = new App(appConfig)
  private val socketModeApp = new SocketModeApp(appToken, app)

  def addEventListener(messageRouter: ActorRef): Unit = {

    val messageEventHandler : BoltEventHandler[MessageEvent] = (payload, context) => {
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
