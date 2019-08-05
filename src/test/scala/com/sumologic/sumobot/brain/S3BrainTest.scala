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
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.amazonaws.auth.{AWSCredentials, AWSStaticCredentialsProvider}
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.sumologic.sumobot.brain.Brain.ValueRetrieved
import com.sumologic.sumobot.core.aws.AWSAccounts
import com.sumologic.sumobot.test.annotated.SumoBotTestKit
import org.scalatest.{BeforeAndAfterAll, Matchers}

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

class S3BrainTest
    extends SumoBotTestKit(ActorSystem("S3SingleObjectBrainTest"))
    with BeforeAndAfterAll
    with Matchers {

  lazy val credsOption = AWSAccounts.load(system.settings.config).values.headOption

  val bucketPrefix = "sumobot-s3-brain"

  // The tests here only run if there are valid AWS credentials in the configuration. Otherwise,
  // they're skipped.
  credsOption foreach {
    creds =>
      cleanupBuckets(creds)

      val bucket = bucketPrefix + randomString(5)

      "S3 brain" should {
        "persist the contents across reloads" in {
          implicit val timeout = Timeout(5.seconds)
          val s3Key = randomString(16)
          val firstBrain = system.actorOf(S3Brain.props(creds, bucket, s3Key))
          firstBrain ! Brain.Store("hello", "world")

          // Just wait for the next message to return.
          val firstRetrieval = firstBrain ? Brain.Retrieve("hello")
          val firstResult = Await.result(firstRetrieval, 5.seconds)
          firstResult match {
            case ValueRetrieved(k, v) =>
              k should be("hello")
              v should be("world")
            case wrongResult => fail(s"Did not get what we expected: $wrongResult")
          }

          // Since we wrote to S3, the 2nd brain should now have the value.
          val secondBrain = system.actorOf(S3Brain.props(creds, bucket, s3Key))
          val secondRetrieval = secondBrain ? Brain.Retrieve("hello")
          val secondResult = Await.result(secondRetrieval, 5.seconds)
          secondResult match {
            case ValueRetrieved(k, v) =>
              k should be("hello")
              v should be("world")
            case wrongResult => fail(s"Did not get what we expected: $wrongResult")
          }
        }
      }
  }

  private def randomString(length: Int): String = {
    val alphabet = ('a' to 'z').mkString + ('0' to '9').mkString
    (1 to length).
        map(_ => Random.nextInt(alphabet.length)).
        map(alphabet.charAt).mkString
  }

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
    credsOption.foreach(cleanupBuckets)
  }

  def cleanupBuckets(creds: AWSCredentials): Unit = {
    val s3 = AmazonS3ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(creds)).build()
    s3.listBuckets().asScala.filter(_.getName.startsWith(bucketPrefix)).foreach {
      bucket =>
        println(s"Deleting S3 bucket ${bucket.getName}")
        val objects = s3.listObjects(bucket.getName).getObjectSummaries.asScala.map(_.getKey)
        objects.foreach {
          obj =>
            s3.deleteObject(bucket.getName, obj)
        }
        s3.deleteBucket(bucket.getName)
    }
  }
}
