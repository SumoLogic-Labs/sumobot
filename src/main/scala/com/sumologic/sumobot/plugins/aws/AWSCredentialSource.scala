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
package com.sumologic.sumobot.plugins.aws

import com.amazonaws.auth.{AWSCredentials, BasicAWSCredentials}
import com.netflix.config.scala.{DynamicStringProperty, DynamicStringListProperty}

object AWSCredentialSource {
  def credentials: Map[String, AWSCredentials] = {
    DynamicStringListProperty("aws.accounts", List.empty, ",")() match {
      case Some(names) =>
        names.map {
          name =>
            for (keyId <- DynamicStringProperty(s"aws.$name.key.id", null)();
                 accessKey <- DynamicStringProperty(s"aws.$name.key.secret", null)())
              yield name -> new BasicAWSCredentials(keyId.trim, accessKey.trim)
        }.flatten.toMap
      case None =>
        Map.empty
    }
  }
}


