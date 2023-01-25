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
import com.sumologic.sumobot.core.model.{IncomingMessage, OutgoingMessage, PublicChannel, UserSender}
import com.sumologic.sumobot.plugins.BotPlugin

import scala.util.Try

trait EscalationPolicyFilter {
  def filter(message: IncomingMessage, onCalls: Seq[PagerDutyOnCall]): Seq[PagerDutyOnCall]
}

object PagerDuty {
  val PageOnCalls = BotPlugin.matchText("page on[\\-]?calls[\\:]? (.*)")
  val WhosOnCall = BotPlugin.matchText("who'?s on\\s?call(?: for (.+?))?\\??")
}

/**
 * @author Chris (chris@sumologic.com)
 */
class PagerDuty extends BotPlugin with ActorLogging {

  override protected def help: String =
    """
      |Communicate with PagerDuty to learn about on-call processes. And stuff.
      |
      |who's on call? - I'll tell you! You can add policy filters as well! Eg. 'whos oncall for infra'
      |page oncalls: <message> - I'll trigger an alert for the on-calls with the message.
    """.stripMargin

  // TODO: Turn these into actual settings
  private val maximumLevel = 2

  private val ignoreTest = true // Ignore policies containing the word test

  private val manager = new PagerDutySchedulesManager(
    PagerDutySettings(config.getString("token"), config.getString("url")))

  private val eventApi = new PagerDutyEventApi()

  private val policyFilter: Option[EscalationPolicyFilter] =
    Try(config.getString("policy-filter")).toOption.
        map {
          n =>
            println(s"CLASS NAME: $n")
            val clazz = Class.forName(n)
            println(s"INSTANTIATING ${clazz.getName}")
            clazz.newInstance().asInstanceOf[EscalationPolicyFilter]
        }

  import PagerDuty._

  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {
    case message@IncomingMessage(WhosOnCall(filter), _, _, _, _, _, _) =>
      message.respondInFuture(whoIsOnCall(_, maximumLevel, Option(filter)))
  }

  private[this] def whoIsOnCall(msg: IncomingMessage,
                                  maximumLevel: Int,
                                  filterOpt: Option[String]): OutgoingMessage = {
    val oncalls = try {
      manager.getAllOnCalls
    } catch {
      case e: Exception =>
        log.error(e, "Pagerduty lookup failure")
        msg.message("Failed to retrieve on-calls from Pagerduty")
        throw e
    }

    val nonTestOnCalls = oncalls.filter {
      oncall =>
        !(ignoreTest && oncall.escalation_policy.summary.toLowerCase.contains("test"))
    }
    val partiallyFilteredOnCalls = nonTestOnCalls.filter {
      oncall =>
        (oncall.escalation_level <= maximumLevel && (filterOpt.isEmpty ||
            filterOpt.exists(filter =>
              oncall.escalation_policy.summary.toLowerCase.contains(filter.toLowerCase))))
    }

    val filteredOnCalls = policyFilter match {
      case Some(filter) => filter.filter(msg, partiallyFilteredOnCalls)
      case None => partiallyFilteredOnCalls
    }

    val outputString =
      filteredOnCalls.groupBy(_.escalation_policy.summary).toList.sortBy(_._1).map {
        tpl =>
          val oncallString = tpl._2.groupBy(_.escalation_level).toList.sortBy(_._1).map {
            levelOnCallsTpl =>
              val oncalls = levelOnCallsTpl._2.map(_.user.summary).sorted.mkString(", ")
              val levelName = levelOnCallsTpl._1 match {
                case 1 => "primary"
                case 2 => "secondary"
                case 3 => "tertiary"
                case other => s"level $other"
              }
              s"- _$levelName:_ $oncalls"
          }.mkString("\n", "\n", "\n")
          "*" + tpl._1 + "*" + oncallString
      }.mkString("\n")
    msg.response(outputString, inThread = true)
  }
}
