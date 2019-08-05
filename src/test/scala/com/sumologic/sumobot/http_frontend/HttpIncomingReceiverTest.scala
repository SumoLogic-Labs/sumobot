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

import java.time.Instant

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.scaladsl.Source
import akka.testkit.{TestActorRef, TestActors, TestKit, TestProbe}
import com.sumologic.sumobot.core.HttpReceptionist
import com.sumologic.sumobot.core.model.IncomingMessage
import com.sumologic.sumobot.test.SumoBotSpec
import com.sumologic.sumobot.test.annotated.SumoBotTestKit
import org.scalatest.BeforeAndAfterAll

class HttpIncomingReceiverTest
  extends SumoBotTestKit(ActorSystem("HttpIncomingReceiverTest"))
  with BeforeAndAfterAll {

  private val probe = new TestProbe(system)
  system.eventStream.subscribe(probe.ref, classOf[IncomingMessage])

  private val dummyActor = TestActorRef(TestActors.blackholeProps)
  private val httpIncomingReceiver = TestActorRef(new HttpIncomingReceiver(dummyActor))

  "HttpIncomingReceiver" should {
    "publish IncomingMessage" when {
      "received streamed TextMessage" in {
        val msgSource = Source(List("hello"))
        val streamedMsg = TextMessage.Streamed(msgSource)

        httpIncomingReceiver ! streamedMsg
        val result = probe.expectMsgClass(classOf[IncomingMessage])
        result.canonicalText should be ("hello")
        result.addressedToUs should be (true)
        result.channel should be (HttpReceptionist.DefaultSumoBotChannel)
        result.attachments should be (Seq.empty)
        result.sentBy.plainTextReference should be (HttpReceptionist.DefaultClientUser.id)
      }

      "received strict TextMessage" in {
        val strictMsg = TextMessage.Strict("hi!")

        httpIncomingReceiver ! strictMsg

        val result = probe.expectMsgClass(classOf[IncomingMessage])
        result.canonicalText should be ("hi!")
        result.addressedToUs should be (true)
        result.channel should be (HttpReceptionist.DefaultSumoBotChannel)
        result.attachments should be (Seq.empty)
        result.sentBy.plainTextReference should be (HttpReceptionist.DefaultClientUser.id)
      }

      "properly format date" when {
        "sending IncomingMessage" in {
          val strictMsg = TextMessage.Strict("test")

          httpIncomingReceiver ! strictMsg
          val result = probe.expectMsgClass(classOf[IncomingMessage])

          val currentDate = Instant.now().getEpochSecond.toDouble
          val messageDate = result.idTimestamp.toDouble

          messageDate should be (currentDate +- 5.0)
        }
      }
    }

    "stop itself and outcoming actor" when {
      "stream ended" in {
        val outcomingActor = TestActorRef(TestActors.blackholeProps)
        val testProbeOutcoming = TestProbe()
        testProbeOutcoming.watch(outcomingActor)

        val shutdownReceiver = TestActorRef(new HttpIncomingReceiver(outcomingActor))
        val testProbeShutdown = TestProbe()
        testProbeShutdown.watch(shutdownReceiver)

        shutdownReceiver ! HttpIncomingReceiver.StreamEnded

        testProbeOutcoming.expectTerminated(outcomingActor)
        testProbeShutdown.expectTerminated(shutdownReceiver)
      }
    }
  }

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
