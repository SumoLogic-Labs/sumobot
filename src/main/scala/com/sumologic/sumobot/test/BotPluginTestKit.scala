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
package com.sumologic.sumobot.test

import akka.actor.ActorSystem
import akka.testkit.{TestProbe, TestKit}
import com.sumologic.sumobot.core.{InstantMessageChannel, IncomingMessage, OutgoingMessage}
import slack.models.User
import scala.concurrent.duration._

import scala.concurrent.duration.FiniteDuration

class BotPluginTestKit(_system: ActorSystem) extends TestKit(_system) {

  protected val outgoingMessageProbe = TestProbe()
  system.eventStream.subscribe(outgoingMessageProbe.ref, classOf[OutgoingMessage])

  protected def confirmOutgoingMessage(test: OutgoingMessage => Unit, timeout: FiniteDuration = 1.second): Unit = {
    outgoingMessageProbe.expectMsgClass(1.second, classOf[OutgoingMessage]) match {
      case msg: OutgoingMessage =>
        test(msg)
    }
  }


  protected def instantMessage(text: String, user: User = mockUser("123", "jshmoe")): IncomingMessage = {
    IncomingMessage(text, true, InstantMessageChannel("125", user), user)
  }

  protected def mockUser(id: String, name: String): User = {
    User(id, name, None, None, None, None, None, None, None, None, None, None)
  }

  protected def send(message: IncomingMessage): Unit = {
    system.eventStream.publish(message)
  }
}
