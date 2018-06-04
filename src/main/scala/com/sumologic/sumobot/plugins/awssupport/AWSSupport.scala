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
package com.sumologic.sumobot.plugins.awssupport

import akka.actor.ActorLogging
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.support.AWSSupportClient
import com.amazonaws.services.support.model.{CaseDetails, DescribeCasesRequest}
import com.sumologic.sumobot.core.aws.AWSAccounts
import com.sumologic.sumobot.core.model.IncomingMessage
import com.sumologic.sumobot.plugins.BotPlugin

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

class AWSSupport
  extends BotPlugin
  with ActorLogging {

  case class CaseInAccount(account: String, caseDetails: CaseDetails)

  private val credentials: Map[String, AWSCredentials] =
    AWSAccounts.load(context.system.settings.config)

  private val clients = credentials.map(tpl => tpl._1 -> new AWSSupportClient(tpl._2))

  override protected def help: String =
    s"""
       |I can tell you about AWS support tickets.
       |
       |list aws cases - List all AWS support tickets.
       |show aws case <case> - I'll show you more details about that case.
     """.stripMargin

  private val CaseDetails = matchText("show aws case (\\d+).*")

  private val ListCases = matchText("list aws cases")

  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {

    case message@IncomingMessage(ListCases(), _, _, _, _, _) =>
      message.respondInFuture {
        msg =>
          val caseList = getAllCases.map(summary(_) + "\n").mkString("\n")
          msg.message(caseList)
      }

    case message@IncomingMessage(CaseDetails(caseId), _, _, _, _, _) =>
      message.respondInFuture {
        msg =>
          log.info(s"Looking for case $caseId")

          Try(getAllCases) match {
            case Success(cases) =>
              cases.find(_.caseDetails.getDisplayId == caseId) match {
                case None =>
                  msg.response("Not a known support case.")
                case Some(cse) =>
                  msg.message(details(cse))
              }
            case Failure(e) if e.getMessage.contains("Invalid case ID:") =>
              msg.response(s"Invalid case ID: $caseId")
          }
      }
  }

  private def getAllCases: Seq[CaseInAccount] = {
    clients.toSeq.par.flatMap {
      tpl =>
        val client = tpl._2
        val unresolved = client.describeCases(new DescribeCasesRequest()).getCases.asScala.toList
        val resolved = client.describeCases(new DescribeCasesRequest().withIncludeResolvedCases(true)).getCases.asScala.toList
        (unresolved ++ resolved).map(CaseInAccount(tpl._1, _))
    }.seq
  }

  private def summary(cia: CaseInAccount): String =
    s"*# ${cia.caseDetails.getDisplayId}:* ${cia.caseDetails.getSubject}\n" +
      s" - account: ${cia.account}, submitted by: ${cia.caseDetails.getSubmittedBy}, status: ${cia.caseDetails.getStatus}"

  private def details(cia: CaseInAccount): String = {
    val latest = cia.caseDetails.getRecentCommunications.getCommunications.asScala.head
    summary(cia) + "\n\n" +
      s"""
         |_${latest.getSubmittedBy} at ${latest.getTimeCreated}_
         |${latest.getBody}
    """.stripMargin
  }
}
