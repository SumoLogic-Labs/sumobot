package com.sumologic.sumobot

import akka.actor.ActorSystem
import com.netflix.config.scala.{DynamicIntProperty, DynamicStringProperty}
import slack.rtm.SlackRtmClient
import scala.concurrent.duration._


object SlackSettings {
  val ApiToken = DynamicStringProperty("slack.api.token", null)
  val ConnectTimeout = DynamicIntProperty("slack.connect.timeout.seconds", 15)

  def connectOrExit(implicit system: ActorSystem): SlackRtmClient = {
    ApiToken() match {
      case Some(token) =>
        SlackRtmClient(token, ConnectTimeout.get.seconds)
      case None =>
        println(s"Please set the slack.api.token environment variable!")
        sys.exit(1)
        null
    }
  }
}
