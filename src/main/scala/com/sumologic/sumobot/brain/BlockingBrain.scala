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
package com.sumologic.sumobot.brain

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.sumologic.sumobot.brain.Brain._

import scala.concurrent.Await
import scala.concurrent.duration._

class BlockingBrain(brain: ActorRef) {

  def retrieve(key: String): Option[String] = {
    implicit val timeout = Timeout(2.seconds)
    Await.result(brain ? Retrieve(key), 2.seconds) match {
      case ValueRetrieved(_, value) => Some(value)
      case ValueMissing(_) => None
    }
  }

  def listValues(prefix: String = ""): Map[String, String] = {
    implicit val timeout = Timeout(2.seconds)
    Await.result(brain ? ListValues(prefix), 2.seconds) match {
      case ValueMap(map) => map
    }
  }

  def store(key: String, value: String): Unit = {
    brain ! Store(key, value)
  }

  def remove(key: String): Unit = {
    brain ! Remove(key)
  }
}
