package com.sumologic.sumobot.plugins.pagerduty

import akka.actor.ActorSystem
import com.sumologic.sumobot.{MatchTextUtil, SumoBotSpec}
import akka.testkit.TestActorRef

/**
 * @author Chris (chris@sumologic.com)
 */
class PagerDutyTest extends SumoBotSpec with MatchTextUtil {

  "PagerDuty.WhosOnCall" should {
    "match expected input" in {
      implicit val actorSystem = ActorSystem("PagerDutyTest")
      val actorRef = TestActorRef(new PagerDuty(null))
      val sut = actorRef.underlyingActor //new PagerDuty(null)

      shouldMatch(sut.WhosOnCall, "who's on call?")
      shouldMatch(sut.WhosOnCall, "who's on call")
      shouldMatch(sut.WhosOnCall, "who's oncall?")
      shouldMatch(sut.WhosOnCall, "who's oncall")
      shouldMatch(sut.WhosOnCall, "whos oncall")

      shouldNotMatch(sut.WhosOnCall, "test")
      actorSystem.shutdown()
    }
  }
}
