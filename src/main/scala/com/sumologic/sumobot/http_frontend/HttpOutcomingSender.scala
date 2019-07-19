package com.sumologic.sumobot.http_frontend

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.model.ws.TextMessage
import com.sumologic.sumobot.core.model.OutgoingMessage

class HttpOutcomingSender(publisherRef: ActorRef) extends Actor with ActorLogging {
  override def preStart(): Unit = {
    Seq(classOf[OutgoingMessage]).foreach(context.system.eventStream.subscribe(self, _))
  }

  override def receive: Receive = {
    case OutgoingMessage(_, text, _) =>
      publisherRef ! TextMessage(text)
  }

  override def postStop(): Unit = {
    context.stop(publisherRef)
    context.system.eventStream.unsubscribe(self)
  }
}
