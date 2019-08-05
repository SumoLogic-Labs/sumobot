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

import com.sumologic.sumobot.http_frontend.SumoBotHttpServerOptions._
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

      val options = fromConfig(config)

      options.httpHost should be ("localhost")
      options.httpPort should be (9999)
      options.origin should be (DefaultOrigin)
      options.authentication should be (DefaultAuthentication)
      options.title should be (DefaultTitle)
      options.description should be (None)
      options.links.isEmpty should be (true)
    }

    "parse config with host, port, origin" in {
      val props = new Properties()
      props.setProperty("host", "localhost")
      props.setProperty("port", "9999")
      props.setProperty("origin", "*")

      val config = ConfigFactory.parseProperties(props)

      val options = fromConfig(config)

      options.httpHost should be ("localhost")
      options.httpPort should be (9999)
      options.origin should be ("*")
      options.authentication should be (DefaultAuthentication)
      options.title should be (DefaultTitle)
      options.description should be (None)
      options.links.isEmpty should be (true)
    }

    "parse config with host, port, origin, basic-authentication" in {
      val props = new Properties()
      props.setProperty("host", "localhost")
      props.setProperty("port", "9999")
      props.setProperty("origin", "https://sumologic.com")
      props.setProperty("authentication.enabled", "true")
      props.setProperty("authentication.class", "com.sumologic.sumobot.http_frontend.authentication.BasicAuthentication")
      props.setProperty("authentication.username", "admin")
      props.setProperty("authentication.password", "hunter2")

      val config = ConfigFactory.parseProperties(props)

      val options = fromConfig(config)

      options.httpHost should be ("localhost")
      options.httpPort should be (9999)
      options.origin should be ("https://sumologic.com")
      options.authentication shouldBe a[BasicAuthentication]
      options.title should be (DefaultTitle)
      options.description should be (None)
      options.links.isEmpty should be (true)
    }

    "parse config with host, port, origin, basic-authentication disabled" in {
      val props = new Properties()
      props.setProperty("host", "localhost")
      props.setProperty("port", "9999")
      props.setProperty("origin", "https://sumologic.com")
      props.setProperty("authentication.enabled", "false")
      props.setProperty("authentication.class", "com.sumologic.sumobot.http_frontend.authentication.BasicAuthentication")
      props.setProperty("authentication.username", "admin")
      props.setProperty("authentication.password", "hunter2")

      val config = ConfigFactory.parseProperties(props)

      val options = fromConfig(config)

      options.httpHost should be ("localhost")
      options.httpPort should be (9999)
      options.origin should be ("https://sumologic.com")
      options.authentication should be (DefaultAuthentication)
      options.title should be (DefaultTitle)
      options.description should be (None)
      options.links.isEmpty should be (true)
    }

    "parse config with host, port, title, description, links" in {
      val props = new Properties()
      props.setProperty("host", "localhost")
      props.setProperty("port", "9999")
      props.setProperty("title", "My Title")
      props.setProperty("description", "My Description")
      props.setProperty("links.link1.name", "Sumo Logic")
      props.setProperty("links.link1.href", "https://sumologic.com")

      val config = ConfigFactory.parseProperties(props)

      val options = fromConfig(config)

      options.httpHost should be ("localhost")
      options.httpPort should be (9999)
      options.origin should be (DefaultOrigin)
      options.authentication should be (DefaultAuthentication)
      options.title should be ("My Title")
      options.description should be (Some("My Description"))
      options.links.length should be (1)
      options.links(0).name should be ("Sumo Logic")
      options.links(0).href should be ("https://sumologic.com")
    }
  }
}
