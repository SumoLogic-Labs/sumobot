package com.sumologic.sumobot.http_frontend

import java.time.Instant

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.javadsl.model.ws.TextMessage
import akka.stream.ActorMaterializer
import com.sumologic.sumobot.core.HttpReceptionist
import com.sumologic.sumobot.core.model.{IncomingMessage, UserSender}

object HttpIncomingReceiver {
  case class StreamEnded()
}

class HttpIncomingReceiver(outcomingRef: ActorRef) extends Actor with ActorLogging {
  private val StrictTimeout = 10000

  private val materializer = ActorMaterializer()

  override def receive: Receive = {
    case msg: TextMessage =>
      val contents = messageContents(msg).trim
      val incomingMessage = IncomingMessage(contents, true, HttpReceptionist.DefaultSumoBotChannel,
        formatDateNow(), None, Seq.empty, UserSender(HttpReceptionist.DefaultClientUser))
      context.system.eventStream.publish(incomingMessage)

    case HttpIncomingReceiver.StreamEnded =>
      context.stop(outcomingRef)
      context.stop(self)
  }

  private def messageContents(msg: TextMessage): String = {
    msg.toStrict(StrictTimeout, materializer).toCompletableFuture.get().getStrictText
  }

  private def formatDateNow(): String = {
    s"${Instant.now().getEpochSecond}.000000"
  }
}
