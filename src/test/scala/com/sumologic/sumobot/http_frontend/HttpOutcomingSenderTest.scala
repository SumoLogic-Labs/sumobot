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
package com.sumologic.sumobot.http_frontend

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.ws.TextMessage
import akka.testkit.{TestActorRef, TestActors, TestKit, TestProbe}
import com.sumologic.sumobot.core.HttpReceptionist
import com.sumologic.sumobot.core.model.{IncomingMessage, OutgoingMessage}
import com.sumologic.sumobot.test.SumoBotSpec
import org.scalatest.BeforeAndAfterAll

class HttpOutcomingSenderTest
  extends TestKit(ActorSystem("HttpOutcomingSenderTest"))
  with SumoBotSpec
  with BeforeAndAfterAll {

  private val probe = new TestProbe(system)
  system.eventStream.subscribe(probe.ref, classOf[TextMessage.Strict])

  private val httpOutcomingSender = TestActorRef(new HttpOutcomingSender(probe.ref))

  "HttpOutcomingSender" should {
    "send TextMessage" when {
      "received OutgoingMessage" in {
        val outgoingMessage = OutgoingMessage(HttpReceptionist.DefaultSumoBotChannel, "hello!")

        system.eventStream.publish(outgoingMessage)

        val result = probe.expectMsgClass(classOf[TextMessage.Strict])
        result.getStrictText should be ("hello!")
      }
    }

    "stop publisher" when {
      "it is stopped" in {
        val dummyActor = TestActorRef(TestActors.blackholeProps)
        val testProbe = TestProbe()
        testProbe.watch(dummyActor)

        val stoppedSender = TestActorRef(new HttpOutcomingSender(dummyActor))
        system.stop(stoppedSender)

        testProbe.expectTerminated(dummyActor)
      }
    }
  }

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
