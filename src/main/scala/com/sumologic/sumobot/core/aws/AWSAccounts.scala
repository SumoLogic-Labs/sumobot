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
package com.sumologic.sumobot.core.aws

import com.amazonaws.auth.{AWSCredentials, BasicAWSCredentials}
import com.sumologic.sumobot.core.config.ListOfConfigs
import com.typesafe.config.Config

object AWSAccounts {
  def load(config: Config): Map[String, AWSCredentials] = {
    ListOfConfigs.parse(config, "aws") {
      (name, accountConfig) =>
        val key = accountConfig.getString(s"key.id")
        val secret = accountConfig.getString(s"key.secret")
        new BasicAWSCredentials(key, secret)
    }
  }
}


