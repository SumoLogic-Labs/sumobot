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
package com.sumologic.sumobot.plugins.jenkins

import com.sumologic.sumobot.test.SumoBotSpec
import com.typesafe.config.ConfigFactory

class JenkinsConfigurationsTest extends SumoBotSpec {

  val emptyConfig = ConfigFactory.parseString("")

  val config = ConfigFactory.parseString(
    """plugins {
      |  jenkins {
      |    enabled = true
      |    connections {
      |      hudson {
      |        url = "https://url1/"
      |        username = "hudson_user"
      |        password = "hudson_password"
      |      }
      |      jenkins {
      |        url = "https://url2/"
      |        username = "jenkins_user"
      |        password = "jenkins_password"
      |      }
      |    }
      |  }
      |}
    """.stripMargin)

  "JenkinsConfigurations" should {
    "parse configurations correctly" in {
      val configs = JenkinsConfigurations.load(config)

      configs("hudson").name should be ("hudson")
      configs("hudson").url should be("https://url1/")
      configs("hudson").username should be("hudson_user")
      configs("hudson").password should be("hudson_password")

      configs("jenkins").name should be ("jenkins")
      configs("jenkins").url should be("https://url2/")
      configs("jenkins").username should be("jenkins_user")
      configs("jenkins").password should be("jenkins_password")
    }

    "return empty list with empty config" in {
      JenkinsConfigurations.load(emptyConfig).isEmpty should be(true)
    }
  }
}
