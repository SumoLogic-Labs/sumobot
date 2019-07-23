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
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.ws.{Message, UpgradeToWebSocket}
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, Uri}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import org.reactivestreams.Publisher

import SumoBotHttpServer._

import scala.concurrent.duration._
import scala.concurrent.Await

object SumoBotHttpServer {
  private[http_frontend] val UrlSeparator = "/"

  private[http_frontend] val RootPage = "index.html"
  private[http_frontend] val WebSocketEndpoint = UrlSeparator + "websocket"

  private[http_frontend] val Resources = Set(UrlSeparator + RootPage, UrlSeparator + "script.js")

  private[http_frontend] val BufferSize = 128
  private[http_frontend] val SocketOverflowStrategy = OverflowStrategy.fail
}

class SumoBotHttpServer(httpHost: String, httpPort: Int)(implicit system: ActorSystem) {
  private implicit val materializer: Materializer = ActorMaterializer()

  private val serverSource = Http().bind(httpHost, httpPort)

  private val binding = serverSource.to(Sink.foreach(_.handleWithSyncHandler(requestHandler))).run()

  def terminate(): Unit = {
    Await.result(binding, 10.seconds).terminate(5.seconds)
  }

  private val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      staticResource(RootPage)

    case req@HttpRequest(GET, Uri.Path(WebSocketEndpoint), _, _, _) =>
      webSocketRequestHandler(req)

    case HttpRequest(GET, Uri.Path(path), _, _, _)
      if Resources.contains(path) =>
      val filename = path.replaceFirst(UrlSeparator, "")
      staticResource(filename)

    case invalid: HttpRequest =>
      invalid.discardEntityBytes()
      HttpResponse(403)
  }

  private def staticResource(filename: String): HttpResponse = {
    val resource = StaticResource(filename)
    HttpResponse(entity = HttpEntity(resource.contentType, resource.contents))
  }

  private def webSocketRequestHandler(req: HttpRequest): HttpResponse = {
    req.header[UpgradeToWebSocket] match {
      case Some(upgrade) =>
        webSocketUpgradeHandler(upgrade)
      case None => HttpResponse(400, entity = "Invalid WebSocket request")
    }
  }

  def webSocketUpgradeHandler(upgrade: UpgradeToWebSocket): HttpResponse = {
    val (publisherRef: ActorRef, publisher: Publisher[Message]) =
      Source.actorRef[Message](BufferSize, SocketOverflowStrategy)
      .toMat(Sink.asPublisher(true))(Keep.both).run()

    val publisherSource = Source.fromPublisher(publisher)

    val senderRef = system.actorOf(Props(classOf[HttpOutcomingSender], publisherRef))
    val receiverRef = system.actorOf(Props(classOf[HttpIncomingReceiver], senderRef))

    val sink = Sink.actorRef(receiverRef, HttpIncomingReceiver.StreamEnded)

    upgrade.handleMessagesWithSinkSource(sink, publisherSource)
  }
}
