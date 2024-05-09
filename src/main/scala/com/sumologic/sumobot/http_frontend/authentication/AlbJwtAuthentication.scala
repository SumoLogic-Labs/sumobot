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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpMethods.{GET, HEAD, OPTIONS}
import org.apache.pekko.http.scaladsl.model.headers.{RawHeader, `Access-Control-Allow-Methods`, `Location`}
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.stream.ActorMaterializer
import com.sumologic.sumobot.http_frontend.authentication.AlbJwtAuthentication._
import com.typesafe.config.Config
import pdi.jwt.{JwtAlgorithm, JwtHeader, JwtJson, JwtOptions}
import play.api.libs.json.Json

import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.Base64
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Success

object AlbJwtAuthentication {
  private val HeaderName = "x-amzn-oidc-data"
  private val FailureResponse = AuthenticationForbidden(HttpResponse(401))
  private val KeyEndpointTimeout = 5.seconds

  private val KeyHeaderBegin = "-----BEGIN PUBLIC KEY-----"
  private val KeyHeaderEnd = "-----END PUBLIC KEY-----"

  private val LogoutEndpoint = "/logout"
  private val LogoutEndpointLink = "logout"
}

class AlbJwtAuthentication(config: Config) extends HttpAuthentication {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  private val keyEndpoint = config.getString("key-endpoint")
  private val cookieName = config.getString("cookie-name")
  private val logoutRedirect = config.getString("logout-redirect")

  override def authentication(request: HttpRequest): AuthenticationResult = {
    request.headers.find(header => header.is(HeaderName)) match {
      case Some(header) => parseJwtString(header.value())
      case _ => FailureResponse
    }
  }

  private val redirectResponse = HttpResponse(StatusCodes.TemporaryRedirect, headers = List(`Location`(logoutRedirect),
    RawHeader("Set-Cookie", s"$cookieName=; Expires=-1; Path=/; Secure; HttpOnly")))

  override def routes: PartialFunction[HttpRequest, HttpResponse] = {
    case req@HttpRequest(GET, Uri.Path(LogoutEndpoint), _, _, _) =>
      redirectResponse

    case req@HttpRequest(HEAD, Uri.Path(LogoutEndpoint), _, _, _) =>
      redirectResponse

    case req@HttpRequest(OPTIONS, Uri.Path(LogoutEndpoint), _, _, _) =>
      HttpResponse()
        .withHeaders(List(`Access-Control-Allow-Methods`(List(GET))))
  }

  private def parseJwtString(jwtString: String): AuthenticationResult = {
    JwtJson.decodeAll(jwtString, JwtOptions(signature = false)) match {
      case Success((header, claim, _)) =>
        downloadKey(header).map {
          key =>
            val publicKey = parseKey(key)

            if (JwtJson.isValid(jwtString, publicKey, Seq(JwtAlgorithm.ES256))) {
              val parsedClaim = Json.parse(claim.content)
              val nameOption = (parsedClaim \\ "name").headOption
              val emailOption = (parsedClaim \\ "email").headOption.map(email => email.as[String])
              nameOption match {
                case Some(name) =>
                  val authMessage = Some(s"Logged in as: ${name.as[String]}")
                  val links = Seq(Link("Log out", LogoutEndpointLink))
                  AuthenticationSucceeded(AuthenticationInfo(authMessage, emailOption, links))
                case None => FailureResponse
              }
            } else {
              FailureResponse
            }
        }.getOrElse(FailureResponse)
      case _ => FailureResponse
    }
  }

  private def downloadKey(header: JwtHeader): Option[String] = {
    header.keyId match {
      case Some(keyId) =>
        val request = HttpRequest(uri = s"$keyEndpoint$keyId")

        val response = Await.result(Http().singleRequest(request), KeyEndpointTimeout)
        val responseString = Await.result(Unmarshal(response.entity).to[String], KeyEndpointTimeout)

        Some(responseString)
      case _ => None
    }
  }

  private def parseKey(key: String): PublicKey = {
    val strippedKey = key.replace(KeyHeaderBegin, "").replace(KeyHeaderEnd, "").replaceAll("\\n", "")
    val keySpec = new X509EncodedKeySpec(Base64.getDecoder.decode(strippedKey))
    val keyFactory = KeyFactory.getInstance("EC")
    keyFactory.generatePublic(keySpec)
  }
}
