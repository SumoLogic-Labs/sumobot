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

import com.sumologic.sumobot.test.annotated.SumoBotSpec
import com.typesafe.config.ConfigFactory

class AWSAccountsTest extends SumoBotSpec {
  val configWithoutAccounts = ConfigFactory.parseString("")

  val configWithAccounts = ConfigFactory.parseString(
    """
      |aws {
      |  infra {
      |    key.id = "infra_keyid"
      |    key.secret = "infra_secret"
      |  }
      |  prod {
      |    key.id = "prod_keyid"
      |    key.secret = "prod_secret"
      |  }
      |}
    """.stripMargin)
  
  "AWSCredentialSource" should {
    "return all credentials configuration" in {
      val result = AWSAccounts.load(configWithAccounts)
      result("infra").getAWSAccessKeyId should be ("infra_keyid")
      result("infra").getAWSSecretKey should be ("infra_secret")
      result("prod").getAWSAccessKeyId should be ("prod_keyid")
      result("prod").getAWSSecretKey should be ("prod_secret")
    }

    "return an empty list when no accounts are in the configuration" in {
      AWSAccounts.load(configWithoutAccounts).isEmpty should be (true)
    }
  }
}
