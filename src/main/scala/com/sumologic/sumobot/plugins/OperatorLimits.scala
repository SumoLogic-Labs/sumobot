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
package com.sumologic.sumobot.plugins

import com.sumologic.sumobot.core.Bootstrap
import com.sumologic.sumobot.core.model.{IncomingMessage, Sender, UserSender}
import slack.models.User

import scala.collection.JavaConverters._

object OperatorLimits {
  private val config = Bootstrap.system.settings.config
  private lazy val operatorNames = config.getStringList("operators").asScala.toSet

  def isOperator(sender: Sender): Boolean =
    sender match {
      case u: UserSender => isOperator(u.slackUser)
      case _ => false
    }

  def isOperator(user: User): Boolean =
    operatorNames.exists(_.trim.equalsIgnoreCase(user.name.trim))
}

trait OperatorLimits {
  this: BotPlugin => 
  
  protected def sentByOperator(message: IncomingMessage): Boolean =
    OperatorLimits.isOperator(message.sentBy)
}
