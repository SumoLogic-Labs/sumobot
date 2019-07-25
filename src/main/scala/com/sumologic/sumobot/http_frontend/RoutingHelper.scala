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

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, ResponseEntity}
import akka.http.scaladsl.model.HttpMethods.{GET, HEAD}
import akka.stream.Materializer

case class RoutingHelper(origin: String)(implicit materializer: Materializer) {
  def withAllowOriginHeader(routing: PartialFunction[HttpRequest, HttpResponse]): PartialFunction[HttpRequest, HttpResponse] = {
    routing.andThen {
      response: HttpResponse =>
        val oldHeaders = response.headers.filter(header => header.isNot(`Access-Control-Allow-Origin`.lowercaseName)).toList
        val newHeaders = oldHeaders :+ `Access-Control-Allow-Origin`(origin)

        response.withHeaders(newHeaders)
    }
  }

  def withHeadRequests(routing: PartialFunction[HttpRequest, HttpResponse]): PartialFunction[HttpRequest, HttpResponse] = {
    routing.orElse {
      case request@HttpRequest(HEAD, _, _, _, _)
        if routing.isDefinedAt(request.withMethod(GET)) =>
        val response = routing(request.withMethod(GET))

        val oldEntity = response.entity
        val newEntity = HttpEntity(oldEntity.contentType, Array.empty[Byte])

        response.withEntity(newEntity)
    }
  }

  def withForbiddenFallback(routing: PartialFunction[HttpRequest, HttpResponse]): PartialFunction[HttpRequest, HttpResponse] = {
    routing.orElse {
      case invalid: HttpRequest =>
        invalid.discardEntityBytes()
        HttpResponse(403)
    }
  }
}
