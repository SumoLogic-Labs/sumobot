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

import java.time.Instant

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.javadsl.model.ws.TextMessage
import akka.stream.ActorMaterializer
import com.sumologic.sumobot.core.HttpReceptionist
import com.sumologic.sumobot.core.model.{IncomingMessage, UserSender}

object HttpIncomingReceiver {
  case class StreamEnded()
}

class HttpIncomingReceiver(outcomingRef: ActorRef) extends Actor with ActorLogging {
  private val StrictTimeout = 10000

  private val materializer = ActorMaterializer()

  override def receive: Receive = {
    case msg: TextMessage =>
      val contents = messageContents(msg).trim
      val incomingMessage = IncomingMessage(contents, true, HttpReceptionist.DefaultSumoBotChannel,
        formatDateNow(), None, Seq.empty, UserSender(HttpReceptionist.DefaultClientUser))
      context.system.eventStream.publish(incomingMessage)

    case HttpIncomingReceiver.StreamEnded =>
      context.stop(outcomingRef)
      context.stop(self)
  }

  private def messageContents(msg: TextMessage): String = {
    msg.toStrict(StrictTimeout, materializer).toCompletableFuture.get().getStrictText
  }

  private def formatDateNow(): String = {
    s"${Instant.now().getEpochSecond}.000000"
  }
}
