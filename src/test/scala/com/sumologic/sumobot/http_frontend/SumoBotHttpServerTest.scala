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

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.http.scaladsl.model.ws.{TextMessage, WebSocketRequest}
import akka.http.scaladsl.model.{ContentTypes, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import com.sumologic.sumobot.brain.InMemoryBrain
import com.sumologic.sumobot.core.{Bootstrap, HttpReceptionist}
import com.sumologic.sumobot.plugins.PluginsFromProps
import com.sumologic.sumobot.plugins.help.Help
import com.sumologic.sumobot.plugins.system.System
import com.sumologic.sumobot.test.SumoBotSpec
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.http.scaladsl.model.ContentTypes._

class SumoBotHttpServerTest
  extends TestKit(ActorSystem("SumoBotHttpServerTest"))
   with SumoBotSpec
   with BeforeAndAfterAll {

  private implicit val materializer = ActorMaterializer()

  private val host = "localhost"
  private val port = 9999
  private val httpServer = new SumoBotHttpServer(host, port)

  private val brain = TestActorRef(Props[InMemoryBrain])
  private val httpReceptionist = TestActorRef(new HttpReceptionist(brain))
  Bootstrap.receptionist = Some(httpReceptionist)

  private val pluginCollection = PluginsFromProps(Array(Props(classOf[Help]), Props(classOf[System])))
  pluginCollection.setup

  "SumoBotHttpServer" should {
    "handle static pages requests" when {
      "accessing /" in {
        sendHttpRequest("/") {
          (response, responseString) =>
            response.status should be (StatusCodes.OK)
            response.header[`Content-Type`] should be(Some(`Content-Type`(`text/html(UTF-8)`)))
            responseString should include("<!doctype html>")
        }
      }

      "accessing /index.html" in {
        sendHttpRequest("/index.html") {
          (response, responseString) =>
            response.status should be (StatusCodes.OK)
            response.header[`Content-Type`] should be(Some(`Content-Type`(`text/html(UTF-8)`)))
            responseString should include("<!doctype html>")
        }
      }

      "accessing invalid page" in {
        sendHttpRequest("/blehbleh") {
          (response, _) =>
            response.status should be (StatusCodes.Forbidden)
        }
      }
    }

    "handle WebSocket requests" when {
      "sending message to Help plugin" in {
        val probe = TestProbe()
        sendWebSocketMessage("help", probe.ref)

        eventually {
          val response = probe.expectMsgClass(classOf[TextMessage.Strict])
          response.getStrictText should include("Help")
          response.getStrictText should include("System")
        }
      }

      "sending message to System plugin" in {
        val probe = TestProbe()
        sendWebSocketMessage("when did you start?", probe.ref)

        eventually {
          val response = probe.expectMsgClass(classOf[TextMessage.Strict])
          response.getStrictText should include("I started at ")
        }
      }

      "sending multiple messages" in {
        val probe = TestProbe()

        sendWebSocketMessages(Array("help", "blahblah invalid command", "help"), probe.ref)

        eventually {
          val firstResponse = probe.expectMsgClass(classOf[TextMessage.Strict])
          firstResponse.getStrictText should include("Help")
        }

        eventually {
          val secondResponse = probe.expectMsgClass(classOf[TextMessage.Strict])
          secondResponse.getStrictText should include("Help")
        }
      }
    }
  }

  private def sendHttpRequest(path: String)(handler: (HttpResponse, String) => Unit): Unit = {
    val responseFuture = Http().singleRequest(httpRequest(path))
    Await.result(responseFuture, 5.seconds) match {
      case response: HttpResponse =>
        val responseStringFuture = Unmarshal(response.entity).to[String]
        val responseString = Await.result(responseStringFuture, 5.seconds)
        handler(response, responseString)
      case _ =>
        fail("received invalid HTTP response")
    }
  }

  private def sendWebSocketMessages(msgs: Seq[String], listenerRef: ActorRef): Unit = {
    val sink = Sink.actorRef(listenerRef, "ended")
    val source = Source(msgs.map(msg => TextMessage(msg)).toList)

    Http().singleWebSocketRequest(webSocketRequest, Flow.fromSinkAndSourceMat(sink, source)(Keep.left))
  }

  private def sendWebSocketMessage(msg: String, listenerRef: ActorRef): Unit = {
    sendWebSocketMessages(Array(msg), listenerRef)
  }

  private def httpRequest(path: String): HttpRequest = {
    HttpRequest(uri = s"http://$host:$port$path")
  }

  private val webSocketRequest = WebSocketRequest(s"ws://$host:$port/websocket")

  override def afterAll: Unit = {
    httpServer.terminate()
    TestKit.shutdownActorSystem(system)
    Bootstrap.receptionist = None
  }
}
