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
import akka.http.scaladsl.{Http, model}
import akka.http.scaladsl.model.HttpMethods.{GET, OPTIONS}
import akka.http.scaladsl.model.ws.{Message, UpgradeToWebSocket}
import akka.http.scaladsl.model.{HttpEntity, HttpHeader, HttpRequest, HttpResponse, Uri}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import org.reactivestreams.Publisher
import SumoBotHttpServer._
import akka.http.scaladsl.model.headers._

import scala.concurrent.duration._
import scala.concurrent.Await

object SumoBotHttpServer {
  val DefaultOrigin = "*"

  private[http_frontend] val UrlSeparator = "/"

  private[http_frontend] val RootPage = "index.html"
  private[http_frontend] val WebSocketEndpoint = UrlSeparator + "websocket"

  private[http_frontend] val Resources = Set(UrlSeparator + "script.js", UrlSeparator + "style.css")

  private[http_frontend] val BufferSize = 128
  private[http_frontend] val SocketOverflowStrategy = OverflowStrategy.fail
}

class SumoBotHttpServer(httpHost: String, httpPort: Int, origin: String)(implicit system: ActorSystem) {
  private implicit val materializer: Materializer = ActorMaterializer()

  private val routingHelper = RoutingHelper(origin)

  private val serverSource = Http().bind(httpHost, httpPort)

  private val binding = serverSource.to(Sink.foreach(_.handleWithSyncHandler(
    routingHelper.withAllowOriginHeader(
      routingHelper.withForbiddenFallback(
        routingHelper.withHeadRequests(
          requestHandler
        )
      )
    )
  ))).run()

  def terminate(): Unit = {
    Await.result(binding, 10.seconds).terminate(5.seconds)
  }

  private val requestHandler: PartialFunction[HttpRequest, HttpResponse] = {
    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      staticResource(RootPage)

    case HttpRequest(OPTIONS, Uri.Path("/"), _, _, _) =>
      staticResourceOptions(RootPage)

    case HttpRequest(GET, Uri.Path(path), _, _, _)
      if Resources.contains(path) =>
      val filename = path.replaceFirst(UrlSeparator, "")
      staticResource(filename)

    case HttpRequest(OPTIONS, Uri.Path(path), _, _, _)
      if Resources.contains(path) =>
      val filename = path.replaceFirst(UrlSeparator, "")
      staticResourceOptions(filename)

    case req@HttpRequest(GET, Uri.Path(WebSocketEndpoint), _, _, _) =>
      webSocketRequestHandler(req)

    case req@HttpRequest(OPTIONS, Uri.Path(WebSocketEndpoint), _, _, _) =>
      webSocketOptions(req)
  }

  private def staticResource(filename: String): HttpResponse = {
    val resource = StaticResource(filename)
    HttpResponse(entity = HttpEntity(resource.contentType, resource.contents))
  }

  private def staticResourceOptions(filename: String): HttpResponse = {
    HttpResponse()
      .withHeaders(List(`Access-Control-Allow-Methods`(List(GET))))
  }

  private def webSocketRequestHandler(req: HttpRequest): HttpResponse = {
    req.header[UpgradeToWebSocket] match {
      case Some(upgrade) =>
        webSocketUpgradeHandler(upgrade)
      case None => HttpResponse(400, entity = "Invalid WebSocket request")
    }
  }

  private def webSocketOptions(req: HttpRequest): HttpResponse = {
    HttpResponse()
      .withHeaders(List(`Access-Control-Allow-Methods`(List(GET))))
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
