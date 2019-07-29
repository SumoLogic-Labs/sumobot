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

import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.Base64

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.sumologic.sumobot.http_frontend.authentication.AlbJwtAuthentication._
import com.typesafe.config.Config
import pdi.jwt.{JwtAlgorithm, JwtHeader, JwtJson, JwtOptions}
import play.api.libs.json.Json

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Success

object AlbJwtAuthentication {
  private val HeaderName = "x-amzn-oidc-data"
  private val FailureResponse = AuthenticationForbidden(HttpResponse(401))
  private val KeyEndpointTimeout = 5.seconds

  private val KeyHeaderBegin = "-----BEGIN PUBLIC KEY-----"
  private val KeyHeaderEnd = "-----END PUBLIC KEY-----"
}

class AlbJwtAuthentication(config: Config) extends HttpAuthentication {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  private val keyEndpoint = config.getString("key-endpoint")

  override def authentication(request: HttpRequest): AuthenticationResult = {
    request.headers.find(header => header.is(HeaderName)) match {
      case Some(header) => parseJwtString(header.value())
      case _ => FailureResponse
    }
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
              nameOption match {
                case Some(name) =>
                  AuthenticationSucceeded(AuthenticationInfo(Some(s"Logged in as: ${name.as[String]}"), Seq.empty))
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
