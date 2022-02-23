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
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpMethods.{GET, HEAD, OPTIONS}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import com.sumologic.sumobot.brain.InMemoryBrain
import com.sumologic.sumobot.core.{Bootstrap, HttpReceptionist}
import com.sumologic.sumobot.http_frontend.authentication.NoAuthentication
import com.sumologic.sumobot.plugins.PluginsFromProps
import com.sumologic.sumobot.plugins.help.Help
import com.sumologic.sumobot.plugins.system.System
import com.sumologic.sumobot.test.annotated.SumoBotTestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

class SumoBotHttpServerTest
  extends SumoBotTestKit(ActorSystem("SumoBotHttpServerTest"))
   with BeforeAndAfterAll {

  private implicit val materializer = ActorMaterializer()

  private val host = "localhost"
  private val port = 9999
  private val origin = "https://sumologic.com"
  private val httpServerOptions = SumoBotHttpServerOptions(host, port, origin,
    new NoAuthentication(ConfigFactory.empty()), "", None, Seq.empty)
  private val httpServer = new SumoBotHttpServer(httpServerOptions)

  private val brain = TestActorRef(Props[InMemoryBrain]())
  private val httpReceptionist = TestActorRef(new HttpReceptionist(brain))

  private val pluginCollection = PluginsFromProps(Array(Props(classOf[Help]), Props(classOf[System])))

  override def beforeAll: Unit = {
    Bootstrap.receptionist = Some(httpReceptionist)
    pluginCollection.setup
  }

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

      "accessing /script.js" in {
        sendHttpRequest("/script.js") {
          (response, responseString) =>
            response.status should be (StatusCodes.OK)
            response.header[`Content-Type`] should be(Some(`Content-Type`(ContentType(MediaTypes.`application/javascript`, HttpCharsets.`UTF-8`))))
            responseString should include("window.addEventListener")
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
        val disconnectPromise = sendWebSocketMessage("help", probe.ref)

        eventually {
          val response = probe.expectMsgClass(classOf[TextMessage.Strict])
          response.getStrictText should include("Help")
          response.getStrictText should include("System")
        }

        disconnectPromise.success(None)
      }

      "sending message to System plugin" in {
        val probe = TestProbe()
        val disconnectPromise = sendWebSocketMessage("when did you start?", probe.ref)

        eventually {
          val response = probe.expectMsgClass(classOf[TextMessage.Strict])
          response.getStrictText should include("I started at ")
        }

        disconnectPromise.success(None)
      }

      "sending multiple messages" in {
        val probe = TestProbe()

        val disconnectPromise = sendWebSocketMessages(Array("help", "blahblah invalid command", "help"),
          probe.ref)

        eventually {
          val firstResponse = probe.expectMsgClass(classOf[TextMessage.Strict])
          firstResponse.getStrictText should include("Help")
        }

        eventually {
          val secondResponse = probe.expectMsgClass(classOf[TextMessage.Strict])
          secondResponse.getStrictText should include("Help")
        }

        disconnectPromise.success(None)
      }
    }

    "send proper AllowOrigin header" when {
      "sending HTTP request" in {
        sendHttpRequest("/") {
          (response, _) =>
            response.header[`Access-Control-Allow-Origin`] should be (Some(`Access-Control-Allow-Origin`(origin)))
        }
      }

      "sending WebSocket request" in {
        val sink = Sink.ignore
        val source = Source.maybe[Message]

        val (upgradeResponse, disconnectPromise) = Http().singleWebSocketRequest(webSocketRequest,
          Flow.fromSinkAndSourceMat(sink, source)(Keep.right))

        val httpResponse = Await.result(upgradeResponse, 5.seconds).response

        httpResponse.header[`Access-Control-Allow-Origin`] should be (Some(`Access-Control-Allow-Origin`(origin)))

        disconnectPromise.success(None)
      }
    }

    "handle OPTIONS requests" when {
      "accessing root page" in {
        sendHttpRequest("/", method = OPTIONS) {
          (response, _) =>
            response.status should be (StatusCodes.OK)
            response.header[`Access-Control-Allow-Methods`] should be (Some(`Access-Control-Allow-Methods`(List(GET))))
        }
      }

      "accessing WebSocket endpoint" in {
        sendHttpRequest("/websocket", method = OPTIONS) {
          (response, _) =>
            response.status should be (StatusCodes.OK)
            response.header[`Access-Control-Allow-Methods`] should be (Some(`Access-Control-Allow-Methods`(List(GET))))
        }
      }
    }

    "handle HEAD requests" in {
      sendHttpRequest("/", method = HEAD) {
        (response, _) =>
          response.status should be (StatusCodes.OK)
          response.header[`Content-Type`] should be(Some(`Content-Type`(`text/html(UTF-8)`)))
          entityToString(response.entity).isEmpty should be (true)
      }
    }
  }

  private def sendHttpRequest(path: String, method: HttpMethod = GET)(handler: (HttpResponse, String) => Unit): Unit = {
    val responseFuture = Http().singleRequest(httpRequest(path, method))
    Await.result(responseFuture, 5.seconds) match {
      case response: HttpResponse =>
        val responseStringFuture = Unmarshal(response.entity).to[String]
        val responseString = Await.result(responseStringFuture, 5.seconds)
        handler(response, responseString)
      case _ =>
        fail("received invalid HTTP response")
    }
  }

  private def sendWebSocketMessages(msgs: Seq[String], listenerRef: ActorRef): Promise[Option[Message]] = {
    val sink = Sink.actorRef(listenerRef, "ended")
    val source = Source(msgs.map(msg => TextMessage(msg)).toList).concatMat(Source.maybe[Message])(Keep.right)

    // NOTE(pgrabowski, 2019-07-23): Using promise to signal when to close WebSocket.
    // https://doc.akka.io/docs/akka-http/current/client-side/websocket-support.html#half-closed-websockets
    val (_, disconnectPromise) = Http().singleWebSocketRequest(webSocketRequest,
      Flow.fromSinkAndSourceMat(sink, source)(Keep.right))

    disconnectPromise
  }

  private def sendWebSocketMessage(msg: String, listenerRef: ActorRef): Promise[Option[Message]] = {
    sendWebSocketMessages(Array(msg), listenerRef)
  }

  private def httpRequest(path: String, method: HttpMethod): HttpRequest = {
    HttpRequest(uri = s"http://$host:$port$path", method = method)
  }

  private def entityToString(httpEntity: HttpEntity): String = {
    Await.result(Unmarshal(httpEntity).to[String], 5.seconds)
  }

  private val webSocketRequest = WebSocketRequest(s"ws://$host:$port/websocket")

  override def afterAll: Unit = {
    httpServer.terminate()
    Bootstrap.receptionist = None
    TestKit.shutdownActorSystem(system, 10.seconds, true)
  }
}
