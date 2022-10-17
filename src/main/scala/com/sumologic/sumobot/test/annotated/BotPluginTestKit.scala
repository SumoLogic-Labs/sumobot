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
package com.sumologic.sumobot.test.annotated

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import com.sumologic.sumobot.core.model.{IncomingMessage, InstantMessageChannel, OutgoingMessage, UserSender}
import org.junit.runner.RunWith
import org.scalatest.concurrent.Eventually
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import slack.models.User

import scala.concurrent.duration.{FiniteDuration, _}

@RunWith(classOf[JUnitRunner])
abstract class BotPluginTestKit(actorSystem: ActorSystem)
  extends TestKit(actorSystem)
    with WordSpecLike with Eventually with Matchers
    with BeforeAndAfterAll {

  protected val outgoingMessageProbe = TestProbe()
  system.eventStream.subscribe(outgoingMessageProbe.ref, classOf[OutgoingMessage])

  protected def confirmOutgoingMessage(test: OutgoingMessage => Unit, timeout: FiniteDuration = 1.second): Unit = {
    outgoingMessageProbe.expectMsgClass(timeout, classOf[OutgoingMessage]) match {
      case msg: OutgoingMessage =>
        test(msg)
    }
  }

  protected def instantMessage(text: String, user: User = mockUser("123", "jshmoe")): IncomingMessage = {
    IncomingMessage(text, true, "125", "1527239216000090", sentBy = UserSender(user))
  }

  protected def mockUser(id: String, name: String): User = {
    User(id, name, None, None, None, None, None, None, None, None, None, None, None, None, None, None)
  }

  protected def send(message: IncomingMessage): Unit = {
    system.eventStream.publish(message)
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
