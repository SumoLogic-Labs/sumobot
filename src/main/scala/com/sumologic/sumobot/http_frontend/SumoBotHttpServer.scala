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
import akka.http.scaladsl.model.HttpMethods.{GET, OPTIONS}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.ws.{Message, WebSocketUpgrade}
import akka.http.scaladsl.model._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.sumologic.sumobot.http_frontend.SumoBotHttpServer._
import com.sumologic.sumobot.http_frontend.authentication.{AuthenticationForbidden, AuthenticationInfo, AuthenticationSucceeded}
import org.reactivestreams.Publisher

import scala.concurrent.Await
import scala.concurrent.duration._

object SumoBotHttpServer {
  private[http_frontend] val UrlSeparator = "/"

  private[http_frontend] val RootPageName = "index.ssp"
  private[http_frontend] val WebSocketEndpoint = UrlSeparator + "websocket"

  private[http_frontend] val Resources = Set(UrlSeparator + "script.js", UrlSeparator + "style.css")

  private[http_frontend] val BufferSize = 128
  private[http_frontend] val SocketOverflowStrategy = OverflowStrategy.fail
}

class SumoBotHttpServer(options: SumoBotHttpServerOptions)(implicit system: ActorSystem) {
  private val routingHelper = RoutingHelper(options.origin)
  private val rootPage = DynamicResource(RootPageName)

  private val serverSource = Http().newServerAt(options.httpHost, options.httpPort).connectionSource()

  private val binding = serverSource.to(Sink.foreach(_.handleWithSyncHandler {
    routingHelper.withAllowOriginHeader {
      routingHelper.withForbiddenFallback {
        options.authentication.routes.orElse {
          routingHelper.withHeadRequests {
            requestHandler
          }
        }
      }
    }
  })).run()

  def terminate(): Unit = {
    Await.result(binding, 10.seconds).terminate(5.seconds)
  }

  private val requestHandler: PartialFunction[HttpRequest, HttpResponse] = {
    case req@HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      authenticate(req) {
        authInfo => renderRootPage(authInfo)
      }

    case HttpRequest(OPTIONS, Uri.Path("/"), _, _, _) =>
      rootPageOptions

    case req@HttpRequest(GET, Uri.Path(path), _, _, _)
      if Resources.contains(path) =>
      val filename = path.replaceFirst(UrlSeparator, "")
      authenticate(req) {
        _ => staticResource(filename)
      }

    case HttpRequest(OPTIONS, Uri.Path(path), _, _, _)
      if Resources.contains(path) =>
      val filename = path.replaceFirst(UrlSeparator, "")
      staticResourceOptions(filename)

    case req@HttpRequest(GET, Uri.Path(WebSocketEndpoint), _, _, _) =>
      authenticate(req) {
        _ => webSocketRequestHandler(req)
      }

    case req@HttpRequest(OPTIONS, Uri.Path(WebSocketEndpoint), _, _, _) =>
      webSocketOptions(req)
  }

  private def authenticate(request: HttpRequest)(succeededHandler: AuthenticationInfo => HttpResponse): HttpResponse = {
    options.authentication.authentication(request) match {
      case AuthenticationSucceeded(info) =>
        succeededHandler(info)
      case AuthenticationForbidden(response) =>
        response
    }
  }

  private def staticResource(filename: String): HttpResponse = {
    val resource = StaticResource(filename)
    HttpResponse(entity = HttpEntity(resource.contentType, resource.contents))
  }

  private def staticResourceOptions(filename: String): HttpResponse = {
    HttpResponse()
      .withHeaders(List(`Access-Control-Allow-Methods`(List(GET))))
  }

  private def renderRootPage(authInfo: AuthenticationInfo): HttpResponse = {
    val contents = rootPage.contents(Map(
      "authInfo" -> authInfo,
      "serverOptions" -> options
    ))
    HttpResponse(entity = HttpEntity(rootPage.contentType, contents))
  }

  private val rootPageOptions: HttpResponse = {
    HttpResponse()
      .withHeaders(List(`Access-Control-Allow-Methods`(List(GET))))
  }

  private def webSocketRequestHandler(req: HttpRequest): HttpResponse = {
    req.attribute(AttributeKeys.webSocketUpgrade) match {
      case Some(upgrade) =>
        webSocketUpgradeHandler(upgrade)
      case None => HttpResponse(400, entity = "Invalid WebSocket request")
    }
  }

  private def webSocketOptions(req: HttpRequest): HttpResponse = {
    HttpResponse()
      .withHeaders(List(`Access-Control-Allow-Methods`(List(GET))))
  }

  def webSocketUpgradeHandler(upgrade: WebSocketUpgrade): HttpResponse = {
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
