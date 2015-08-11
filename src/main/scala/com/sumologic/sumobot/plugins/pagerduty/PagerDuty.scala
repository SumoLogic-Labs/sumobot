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

import akka.actor.ActorLogging
import com.google.common.annotations.VisibleForTesting
import com.sumologic.sumobot.core.model.{PublicChannel, IncomingMessage, OutgoingMessage}
import com.sumologic.sumobot.plugins.BotPlugin

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

trait EscalationPolicyFilter {
  def filter(message: IncomingMessage, policies: Seq[PagerDutyEscalationPolicy]): Seq[PagerDutyEscalationPolicy]
}

/**
 * @author Chris (chris@sumologic.com)
 */
class PagerDuty(manager: PagerDutySchedulesManager,
                policyFilter: Option[EscalationPolicyFilter]) extends BotPlugin with ActorLogging {

  override protected def help: String =
    """
      |Communicate with PagerDuty to learn about on-call processes. And stuff.
      |
      |who's on call? - I'll tell you!
      |page oncalls: <message> - I'll trigger an alert for the on-calls with the message.
    """.stripMargin

  // TODO: Turn these into actual settings
  private val maximumLevel = 2

  private val ignoreTest = true // Ignore policies containing the word test

  private val eventApi = new PagerDutyEventApi()

  @VisibleForTesting protected[pagerduty] val WhosOnCall = matchText("who'?s on\\s?call(?: for (.+?))?\\??")

  private val PageOnCalls = matchText("page on[\\-]?calls[\\:]? (.*)")

  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {
    case message@IncomingMessage(WhosOnCall(filter), _, _, _) =>
      message.respondInFuture(whoIsOnCall(_, maximumLevel, Option(filter)))

    case message@IncomingMessage(PageOnCalls(text), _, PublicChannel(_, channel), _) =>
      pagerDutyKeyFor(channel) match  {
        case Some(key) =>
          eventApi.page(channel, key, s"${message.sentByUser.name} on $channel: $text")
          message.say("Paged on-calls.")
        case None =>
          message.respond("Don't know how to page people in this channel.")
      }
  }

  private def pagerDutyKeyFor(channel: String): Option[String] = {
    Try(context.system.settings.config.getString(s"plugins.pagerduty.service.$channel")).toOption
  }

  private[this] def whoIsOnCall(msg: IncomingMessage,
                                maximumLevel: Int,
                                filterOpt: Option[String]): OutgoingMessage = {
    manager.getEscalationPolicies match {
      case Some(policies) =>
        val escalationPolicies = policies.escalation_policies
        val nonTestPolicies = escalationPolicies.filter {
          policy => !(ignoreTest && policy.name.toLowerCase.contains("test"))
        }

        // TODO: Teach the filter to be smarter about how it handles stuff since this text matching is stupidly simple
        val partiallyFilteredPolicies = nonTestPolicies.filter {
          policy =>
            (filterOpt.isEmpty ||
              filterOpt.exists(filter => policy.name.toLowerCase.contains(filter.toLowerCase))) &&
              policy.on_call.nonEmpty
        }

        val nonFilteredPolicies = policyFilter match {
          case Some(filter) => filter.filter(msg, partiallyFilteredPolicies)
          case None => partiallyFilteredPolicies
        }

        if (nonFilteredPolicies.isEmpty) {
          msg.response("No escalation policies matched your filter.")
        } else {
          val outputString = nonFilteredPolicies.map {
            policy =>
              val onCalls = policy.on_call.filter(_.level <= maximumLevel).groupBy(_.level).toList.sortBy(_._1).map {
                tpl =>
                  val level = tpl._1
                  val oncalls = tpl._2.map(_.user.name).sorted.mkString(", ")
                  val levelName = level match {
                    case 1 => "primary"
                    case 2 => "secondary"
                    case 3 => "tertiary"
                    case other => s"level $other"
                  }
                  s"- _$levelName:_ $oncalls"
              }.mkString("\n", "\n", "\n")

              "*" + policy.name + "*" + onCalls
          }.mkString("\n")

          msg.message(outputString)
        }

      case None =>
        msg.response("Unable to login or something.")
    }
  }
}
