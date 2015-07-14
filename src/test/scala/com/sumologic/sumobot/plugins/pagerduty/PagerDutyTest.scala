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
