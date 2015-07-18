package com.sumologic.sumobot.brain

import akka.actor.Actor

class InMemoryBrain extends Actor {

  import Brain._

  private var brainContents = Map[String, String]()

  override def receive = {
    case Store(key, value) =>
      brainContents += (key -> value)

    case Remove(key) =>
      brainContents -= key

    case Retrieve(key) =>
      brainContents.get(key) match {
        case Some(value) => sender() ! ValueRetrieved(key, value)
        case None => sender() ! ValueMissing(key)
      }

    case ListValues(prefix) =>
      sender() ! ValueMap(brainContents.filter(_._1.startsWith(prefix)))
  }
}
