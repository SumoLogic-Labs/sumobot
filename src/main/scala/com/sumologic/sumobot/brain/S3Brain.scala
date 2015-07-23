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

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.Properties

import akka.actor.{Props, Actor}
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.sumologic.sumobot.brain.Brain._

import scala.collection.JavaConverters._
import scala.collection.immutable

object S3Brain {
  def props(credentials: AWSCredentials,
            bucket: String,
            s3Key: String): Props = Props(classOf[S3Brain], credentials, bucket, s3Key)
}

class S3Brain(credentials: AWSCredentials,
              bucket: String,
              s3Key: String) extends Actor {

  private val s3Client = new AmazonS3Client(credentials)

  private var brainContents: Map[String, String] = loadFromS3()

  override def receive: Receive = {
    case Store(key, value) =>
      brainContents += (key -> value)
      saveToS3(brainContents)

    case Remove(key) =>
      brainContents -= key
      saveToS3(brainContents)

    case Retrieve(key) =>
      brainContents.get(key) match {
        case Some(value) => sender() ! ValueRetrieved(key, value)
        case None => sender() ! ValueMissing(key)
      }

    case ListValues(prefix) =>
      sender() ! ValueMap(brainContents.filter(_._1.startsWith(prefix)))
  }

  private def loadFromS3(): Map[String, String] = {
    val props = new Properties()
    props.load(s3Client.getObject(bucket, s3Key).getObjectContent)
    immutable.Map(props.asScala.toSeq: _*)
  }

  private def saveToS3(contents: Map[String, String]): Unit = {

    if (!s3Client.doesBucketExist(bucket)) {
      s3Client.createBucket(bucket)
    }

    val props = new Properties()
    props.putAll(contents.asJava)
    val out = new ByteArrayOutputStream()
    props.store(out, "")
    out.flush()
    out.close()
    val in = new ByteArrayInputStream(out.toByteArray)
    s3Client.putObject(bucket, s3Key, in, new ObjectMetadata())
  }
}
