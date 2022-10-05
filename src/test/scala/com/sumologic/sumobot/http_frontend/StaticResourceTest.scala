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

import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpCharsets, MediaTypes}
import com.sumologic.sumobot.test.annotated.SumoBotSpec

class StaticResourceTest extends SumoBotSpec {
  "StaticResource" should {
    "return correct content type" when {
      "passing .html file" in {
        StaticResource("test.html").contentType should
          be(ContentTypes.`text/html(UTF-8)`)
      }

      "passing .css file" in {
        StaticResource("test.css").contentType should
          be(ContentType(MediaTypes.`text/css`, HttpCharsets.`UTF-8`))
      }

      "passing unknown extension" in {
        StaticResource("test.unknown").contentType should
          be(ContentTypes.`text/html(UTF-8)`)
      }
    }
  }
}
