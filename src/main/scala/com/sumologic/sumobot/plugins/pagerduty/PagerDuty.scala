package com.sumologic.sumobot.plugins.pagerduty

import akka.actor.ActorLogging
import com.sumologic.sumobot.Bender.{SendSlackMessage, BotMessage}
import com.sumologic.sumobot.plugins.BotPlugin

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author Chris (chris@sumologic.com)
 */
class PagerDuty extends BotPlugin with ActorLogging {

  // TODO: Turn these into actual settings
  val maximumLevel = 2
  val ignoreTest = true // Ignore policies containing the word test

  private lazy val token: String = sys.env.getOrElse("PAGERDUTY_TOKEN", {
    throw new Exception("Please provide a PagerDuty token.")
  })
  private lazy val pagerdutyUrl: String = sys.env.getOrElse("PAGERDUTY_URL", {
    throw new Exception("Please provide a PagerDuty URL.")
  })
  private lazy val manager = new PagerDutySchedulesManager(token, pagerdutyUrl)

  private val WhosOnCall = matchText("who's oncall\\??")

  override protected def receiveText: ReceiveText = {
    case WhosOnCall() => respondInFuture {
      msg => whoIsOnCall(msg, maximumLevel)
    }
  }

  private[this] def whoIsOnCall(msg: BotMessage, maximumLevel: Int): SendSlackMessage = {
    manager.getEscalationPolicies match {
      case Some(policies) =>
        val string = policies.escalation_policies.filter {
          policy => !(ignoreTest && policy.name.toLowerCase.contains("test"))
        }.map {
          policy =>
            val onCalls = policy.on_call.filter(_.level <= maximumLevel).map {
              onCall => s"- level ${onCall.level}: ${onCall.user.name} (${onCall.user.email})"
            }.mkString("\n", "\n", "\n")

            policy.name + onCalls
        }.mkString("\n")
        msg.response(string)
      case None => msg.response("Unable to login or something.")
    }
  }

  override protected def name: String = "pagerduty"

  override protected def help: String =
    """
      | Communicate with PagerDuty to learn about on-call processes. And stuff.
    """.stripMargin
}
