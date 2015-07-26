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

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import com.sumologic.sumobot.core.aws.AWSCredentialSource
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import akka.pattern.ask
import scala.concurrent.duration._

import scala.util.Random

class S3BrainTest
  extends TestKit(ActorSystem("S3SingleObjectBrainTest"))
  with WordSpecLike
  with BeforeAndAfterAll
  with Matchers {

  private val creds = AWSCredentialSource.credentials

  if (creds.nonEmpty) {

    "S3 brain" should {
      "persist the contents across reloads" in {
        implicit val timeout = Timeout(5.seconds)
        val s3Key = randomS3Key
        val firstBrain = system.actorOf(S3Brain.props(creds.head._2, "sumobot-s3-brain", s3Key))
        firstBrain ! Brain.Store("hello", "world")

        // Just wait for the next message to return.
        firstBrain ? Brain.Retrieve("hello")

        // Since we wrote to S3, the 2nd brain should now have the value.
        val secondBrain = system.actorOf(S3Brain.props(creds.head._2, "sumobot-s3-brain", s3Key))
        secondBrain ? Brain.Retrieve("hello")
      }
    }
  }

  private val s3KeyAlphabet = ('a' to 'z').mkString + ('A' to 'Z').mkString + ('0' to '9').mkString

  private def randomS3Key: String = (1 to 16).
    map(_ => Random.nextInt(s3KeyAlphabet.length)).
    map(s3KeyAlphabet.charAt).mkString

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}
