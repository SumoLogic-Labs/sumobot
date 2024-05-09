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

import org.apache.pekko.http.scaladsl.model.HttpMethods.GET
import org.apache.pekko.http.scaladsl.model.headers._
import org.apache.pekko.http.scaladsl.model.{HttpRequest, StatusCodes, Uri}
import com.sumologic.sumobot.test.annotated.SumoBotSpec
import com.typesafe.config.ConfigFactory

import scala.jdk.CollectionConverters._

class BasicAuthenticationTest extends SumoBotSpec {
  private val authConfig = ConfigFactory.parseMap(
    Map("username" -> "admin", "password" -> "hunter2").asJava)
  val basicAuthentication = new BasicAuthentication(authConfig)
  val base64Credentials = "YWRtaW46aHVudGVyMg=="
  val base64InvalidCredentials = "YWRtaW46aHVpdGVyMg=="

  val rootRequest = HttpRequest(GET, Uri("/"))
  val authorizedRootRequest = rootRequest.withHeaders(List(`Authorization`(GenericHttpCredentials("basic", base64Credentials))))
  val invalidRootRequest = rootRequest.withHeaders(List(`Authorization`(GenericHttpCredentials("basic", base64InvalidCredentials))))

  "BasicAuthentication" should {
      "return 401 Unauthorized" when {
        "unauthenticated" in {
          val result = basicAuthentication.authentication(rootRequest)
          result match {
            case AuthenticationForbidden(response) =>
              response.status should be(StatusCodes.Unauthorized)
              response.header[`WWW-Authenticate`].nonEmpty should be(true)
            case _ =>
              fail("expected AuthenticationForbidden")
          }
        }
      }

    "successfuly authenticate" when {
      "provided correct Authorization header" in {
        val result = basicAuthentication.authentication(authorizedRootRequest)
        result match {
          case AuthenticationSucceeded(info) =>
            info.authMessage match {
              case Some(message) =>
                message should include("admin")
              case _ => fail("expected authMessage")
            }
          case _ =>
            fail("expected AuthenticationSucceeded")
        }
      }
    }

    "return 403 Forbidden" when {
      "provided incorrect Authorization header" in {
        val result = basicAuthentication.authentication(invalidRootRequest)
        result match {
          case AuthenticationForbidden(response) =>
            response.status should be(StatusCodes.Forbidden)
          case _ =>
            fail("expected AuthenticationForbidden")
        }
      }
    }
  }
}
