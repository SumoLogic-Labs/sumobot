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
package com.sumologic.sumobot.http_frontend.authentication

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.testkit.{TestActorRef, TestKit}
import com.sumologic.sumobot.brain.InMemoryBrain
import com.sumologic.sumobot.core.{Bootstrap, HttpReceptionist}
import com.sumologic.sumobot.http_frontend.{SumoBotHttpServer, SumoBotHttpServerOptions}
import com.sumologic.sumobot.plugins.PluginsFromProps
import com.sumologic.sumobot.plugins.help.Help
import com.sumologic.sumobot.plugins.system.System
import com.sumologic.sumobot.test.annotated.SumoBotTestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class AuthenticatedServerTest
  extends SumoBotTestKit(ActorSystem("AuthenticatedServerTest"))
    with BeforeAndAfterAll {
  private implicit val materializer = ActorMaterializer()

  private val host = "localhost"
  private val port = 10000
  private val origin = "https://sumologic.com"

  private val authConfig = ConfigFactory.parseMap(
    Map("username" -> "admin", "password" -> "hunter2").asJava)

  private val basicAuthentication = new BasicAuthentication(authConfig)
  private val base64Credentials = "YWRtaW46aHVudGVyMg=="
  private val base64InvalidCredentials = "YWRtaW46aHVpdGVyMg=="

  private val rootPageRequest = httpRequest("/", GET)
  private val stylePageRequest = httpRequest("/style.css", GET)
  private val validRootPageRequest = httpRequest("/", GET, List(`Authorization`(GenericHttpCredentials("basic", base64Credentials))))
  private val invalidRootPageRequest = httpRequest("/", GET, List(`Authorization`(GenericHttpCredentials("basic", base64InvalidCredentials))))

  private val webSocketRequest = WebSocketRequest(s"ws://$host:$port/websocket")
  private val authWebSocketRequest = WebSocketRequest(s"ws://$host:$port/websocket",
    extraHeaders = List(`Authorization`(GenericHttpCredentials("basic", base64Credentials))))

  private val httpServerOptions = SumoBotHttpServerOptions(host, port, origin, basicAuthentication, "", None, Seq.empty)
  private val httpServer = new SumoBotHttpServer(httpServerOptions)

  private val brain = TestActorRef(Props[InMemoryBrain])
  private val httpReceptionist = TestActorRef(new HttpReceptionist(brain))

  private val pluginCollection = PluginsFromProps(Array(Props(classOf[Help]), Props(classOf[System])))

  override def beforeAll: Unit = {
    Bootstrap.receptionist = Some(httpReceptionist)
    pluginCollection.setup
  }

  "authenticated SumoBotHttpServer" should {
    "deny unauthenticated requests" when {
      "accessing root page" in {
        val response = sendHttpRequest(rootPageRequest)
        response.status should be(StatusCodes.Unauthorized)
      }

      "accessing root page with invalid credentials" in {
        val response = sendHttpRequest(invalidRootPageRequest)
        response.status should be(StatusCodes.Forbidden)
      }

      "accessing /style.css" in {
        val response = sendHttpRequest(stylePageRequest)
        response.status should be(StatusCodes.Unauthorized)
      }

      "accessing /websocket" in {
        webSocketResponse(webSocketRequest).status should be (StatusCodes.Unauthorized)
      }
    }

    "allow authenticated requests" when {
      "accessing root page" in {
        val response = sendHttpRequest(validRootPageRequest)
        response.status should be(StatusCodes.OK)
        response.header[`Content-Type`] should be(Some(`Content-Type`(`text/html(UTF-8)`)))

        entityToString(response.entity) should include("<!doctype html>")
      }

      "accessing /websocket" in {
        webSocketResponse(authWebSocketRequest).status should be (StatusCodes.SwitchingProtocols)
      }
    }
  }

  private def sendHttpRequest(httpRequest: HttpRequest): HttpResponse = {
    val responseFuture = Http().singleRequest(httpRequest)
    Await.result(responseFuture, 5.seconds) match {
      case response: HttpResponse =>
        response
      case _ =>
        fail("expected HttpResponse")
    }
  }

  private def entityToString(httpEntity: HttpEntity): String = {
    Await.result(Unmarshal(httpEntity).to[String], 5.seconds)
  }

  private def httpRequest(path: String, method: HttpMethod, headers: immutable.Seq[HttpHeader] = immutable.Seq.empty): HttpRequest = {
    HttpRequest(uri = s"http://$host:$port$path", method = method, headers = headers)
  }

  private def webSocketResponse(request: WebSocketRequest): HttpResponse = {
    val sink = Sink.ignore
    val source = Source.maybe[Message]

    val (upgradeResponse, disconnectPromise) = Http().singleWebSocketRequest(request,
      Flow.fromSinkAndSourceMat(sink, source)(Keep.right))

    val httpResponse = Await.result(upgradeResponse, 5.seconds).response

    disconnectPromise.success(None)

    httpResponse
  }

  override def afterAll: Unit = {
    httpServer.terminate()
    Bootstrap.receptionist = None
    TestKit.shutdownActorSystem(system, 10.seconds, true)
  }
}
