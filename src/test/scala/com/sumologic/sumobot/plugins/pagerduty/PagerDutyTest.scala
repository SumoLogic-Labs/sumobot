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
package com.sumologic.sumobot.plugins.pagerduty

import akka.actor.ActorSystem
import akka.testkit.TestActorRef
import com.sumologic.sumobot.test.{MatchTextUtil, SumoBotSpec}

/**
 * @author Chris (chris@sumologic.com)
 */
class PagerDutyTest extends SumoBotSpec with MatchTextUtil {

  "PagerDuty.WhosOnCall" should {
    "match expected input" in {
      implicit val actorSystem = ActorSystem("PagerDutyTest")
      val actorRef = TestActorRef(new PagerDuty(null, None))
      val sut = actorRef.underlyingActor //new PagerDuty(null)

      shouldMatch(sut.WhosOnCall, "who's on call?")
      shouldMatch(sut.WhosOnCall, "who's on call")
      shouldMatch(sut.WhosOnCall, "who's oncall?")
      shouldMatch(sut.WhosOnCall, "who's oncall")
      shouldMatch(sut.WhosOnCall, "whos oncall")

      "who's oncall for prod?" match {
        case sut.WhosOnCall(filter) => filter should be ("prod")
        case _ => fail("Did not match filter case")
      }

      shouldNotMatch(sut.WhosOnCall, "test")
      actorSystem.shutdown()
    }
  }
}
