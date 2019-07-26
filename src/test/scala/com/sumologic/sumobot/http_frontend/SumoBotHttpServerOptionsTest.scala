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

import java.util.Properties

import com.sumologic.sumobot.http_frontend.authentication.BasicAuthentication
import com.sumologic.sumobot.test.SumoBotSpec
import com.typesafe.config.ConfigFactory

class SumoBotHttpServerOptionsTest extends SumoBotSpec {
  "SumoBotHttpServerOptions" should {
    "parse config with host, port" in {
      val props = new Properties()
      props.setProperty("host", "localhost")
      props.setProperty("port", "9999")

      val config = ConfigFactory.parseProperties(props)

      val options = SumoBotHttpServerOptions.fromConfig(config)

      options.httpHost should be ("localhost")
      options.httpPort should be (9999)
      options.origin should be (SumoBotHttpServer.DefaultOrigin)
      options.authentication should be (SumoBotHttpServer.DefaultAuthentication)
    }

    "parse config with host, port, origin" in {
      val props = new Properties()
      props.setProperty("host", "localhost")
      props.setProperty("port", "9999")
      props.setProperty("origin", "*")

      val config = ConfigFactory.parseProperties(props)

      val options = SumoBotHttpServerOptions.fromConfig(config)

      options.httpHost should be ("localhost")
      options.httpPort should be (9999)
      options.origin should be ("*")
      options.authentication should be (SumoBotHttpServer.DefaultAuthentication)
    }

    "parse config with host, port, origin, basic-authentication" in {
      val props = new Properties()
      props.setProperty("host", "localhost")
      props.setProperty("port", "9999")
      props.setProperty("origin", "https://sumologic.com")
      props.setProperty("basic-authentication.username", "admin")
      props.setProperty("basic-authentication.password", "hunter2")

      val config = ConfigFactory.parseProperties(props)

      val options = SumoBotHttpServerOptions.fromConfig(config)

      options.httpHost should be ("localhost")
      options.httpPort should be (9999)
      options.origin should be ("https://sumologic.com")
      options.authentication shouldBe a[BasicAuthentication]
    }
  }
}
