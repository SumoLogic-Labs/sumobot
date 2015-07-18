package com.sumologic.sumobot.brain

object Brain {
  case class Store(key: String, value: String)
  case class Remove(key: String)
  case class Retrieve(key: String)
  case class ListValues(prefix: String = "")
  case class ValueMap(map: Map[String, String])
  case class ValueRetrieved(key: String, value: String)
  case class ValueMissing(key: String)
}
