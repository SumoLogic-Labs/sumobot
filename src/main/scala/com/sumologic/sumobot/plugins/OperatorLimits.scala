package com.sumologic.sumobot.plugins

import com.netflix.config.scala.DynamicStringListProperty
import com.sumologic.sumobot.core.model.IncomingMessage
import slack.models.User

object OperatorLimits {
  private lazy val operatorNames = DynamicStringListProperty("operators", List.empty, ",")

  def isOperator(user: User): Boolean =
    operatorNames.get.exists(_.trim.equalsIgnoreCase(user.name.trim))
}

trait OperatorLimits {
  this: BotPlugin => 
  
  protected def sentByOperator(message: IncomingMessage): Boolean =
    OperatorLimits.isOperator(message.sentByUser)
}
