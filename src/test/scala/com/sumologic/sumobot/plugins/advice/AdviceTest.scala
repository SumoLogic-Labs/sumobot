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
package com.sumologic.sumobot.plugins.advice

import akka.actor.{ActorSystem, Props}
import com.sumologic.sumobot.plugins.BotPlugin.InitializePlugin
import com.sumologic.sumobot.test.annotated.BotPluginTestKit

class AdviceTest extends BotPluginTestKit(ActorSystem("AdviceTest"))  {

  val adviceRef = system.actorOf(Props[Advice], "advice")
  adviceRef ! InitializePlugin(null, null, null)

  "advice" should {

    "match regexes" in {
      "what should I do about beer" should fullyMatch regex Advice.AdviceAbout
      "what should I do about beer and chips" should fullyMatch regex Advice.AdviceAbout
      "what do you think about beer and chips" should fullyMatch regex Advice.AdviceAbout
      "how do you handle cocktails" should fullyMatch regex Advice.AdviceAbout
    }

    // TODO: Fix this test and reenable.
    "retrieve advice" ignore {
      adviceRef ! instantMessage("I need some advice")
      confirmOutgoingMessage {
        msg =>
          println(s"ADVICE: ${msg.text}")
          msg.text should not include("No advice")
      }
    }
  }
}
