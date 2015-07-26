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
