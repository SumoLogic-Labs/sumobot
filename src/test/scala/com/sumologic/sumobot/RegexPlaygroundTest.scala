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
package com.sumologic.sumobot

import com.sumologic.sumobot.plugins.BotPlugin
import com.sumologic.sumobot.test.SumoBotSpec

class RegexPlaygroundTest extends SumoBotSpec {
  "BotPlugin.matchText()" should {
    "be case insensitive" in {
      val testRegex = BotPlugin.matchText("hello")
      "hello" should fullyMatch regex testRegex
      "Hello" should fullyMatch regex testRegex
      "HellO" should fullyMatch regex testRegex
    }

    "support or via pipe" in {
      val testRegex = BotPlugin.matchText("(hello|world)")
      "hello" should fullyMatch regex testRegex
      "world" should fullyMatch regex testRegex
    }

    "extract stuff between pipes" in {
      val testRegex = BotPlugin.matchText("hello (\\w+) world!")
      "Hello beautiful world!" match {
        case testRegex(kind) => kind should be ("beautiful")
        case _ => fail("Did not match!")
      }
    }

    "extract stuff between pipes with or" in {
      val testRegex = BotPlugin.matchText("hello (\\d+|beautiful) world!")
      "Hello beautiful world!" match {
        case testRegex(kind) => kind should be ("beautiful")
        case _ => fail("Did not match!")
      }
      "Hello 123 world!" match {
        case testRegex(kind) => kind should be ("123")
        case _ => fail("Did not match!")
      }
      "Hello NOT world!" match {
        case testRegex(kind) => fail("Should not match!")
        case _ =>
      }
    }

    "support conditional :" in {
      val testRegex = BotPlugin.matchText("tell <@(\\w+)>[:]?\\s*(.*)")

      "tell <@ABCDEF> blah" match {
        case testRegex(_, text) => text should be ("blah")
        case _ => fail("Did not match!")
      }

      "tell <@ABCDEF>: blah" match {
        case testRegex(_, text) => text should be ("blah")
        case _ => fail("Did not match!")
      }
    }
  }
}
