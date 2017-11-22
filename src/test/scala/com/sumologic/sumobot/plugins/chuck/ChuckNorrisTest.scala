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
package com.sumologic.sumobot.plugins.chuck

import com.sumologic.sumobot.test.SumoBotSpec
import play.api.libs.json.Json

class ChuckNorrisTest extends SumoBotSpec {
  val jsonText = "{ \"type\": \"success\", \"value\": { \"id\": 518, \"joke\": \"Chuck Norris doesn't cheat death. He wins fair and square.\", \"categories\": [] } }"

  "ChuckNorris" should {
    "parse JSON" in {
      val json = Json.parse(jsonText)
      val joke = json \ "value" \ "joke"
      joke.as[String] should be ("Chuck Norris doesn't cheat death. He wins fair and square.")
    }
  }
}
