package com.sumologic.sumobot.plugins.pagerduty

import akka.actor.ActorLogging
import com.google.common.annotations.VisibleForTesting
import com.sumologic.sumobot.Bender.{SendSlackMessage, BotMessage}
import com.sumologic.sumobot.plugins.BotPlugin

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author Chris (chris@sumologic.com)
 */
class PagerDuty(manager: PagerDutySchedulesManager) extends BotPlugin with ActorLogging {

  // TODO: Turn these into actual settings
  val maximumLevel = 2
  val ignoreTest = true // Ignore policies containing the word test

  @VisibleForTesting protected[pagerduty] val WhosOnCall = matchText("who'?s on\\s?call(?: for (.+?))?\\??")

  override protected def receiveText: ReceiveText = {
    case WhosOnCall(filter) => respondInFuture (whoIsOnCall(_, maximumLevel, Option(filter)))
  }

  private[this] def whoIsOnCall(msg: BotMessage, maximumLevel: Int, filterOpt: Option[String]): SendSlackMessage = {
    manager.getEscalationPolicies match {
      case Some(policies) =>
        val escalationPolicies = policies.escalation_policies
        val nonTestPolicies = escalationPolicies.filter {
          policy => !(ignoreTest && policy.name.toLowerCase.contains("test"))
        }

        // TODO: Teach the filter to be smarter about how it handles stuff since this text matching is stupidly simple
        val nonFilteredPolicies = nonTestPolicies.filter {
          policy => filterOpt.isEmpty || filterOpt.exists (filter => policy.name.toLowerCase.contains(filter.toLowerCase))
        }

        if (nonFilteredPolicies.isEmpty) {
          msg.response("No escalation policies matched your filter.")
        } else {
          val outputString = nonFilteredPolicies.map {
            policy =>
              val onCalls = policy.on_call.filter(_.level <= maximumLevel).map {
                onCall => s"- level ${onCall.level}: ${onCall.user.name} (${onCall.user.email})"
              }.mkString("\n", "\n", "\n")

              policy.name + onCalls
          }.mkString("\n")

          msg.response(outputString)
        }

      case None =>
        msg.response("Unable to login or something.")
    }
  }

  override protected def name: String = "pagerduty"

  override protected def help: String =
    """
      | Communicate with PagerDuty to learn about on-call processes. And stuff.
    """.stripMargin
}
