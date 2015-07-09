package com.sumologic.sumobot.plugins.awssupport

import akka.actor.ActorLogging
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.support.AWSSupportClient
import com.amazonaws.services.support.model.{CaseDetails, DescribeCasesRequest}
import com.sumologic.sumobot.plugins.BotPlugin

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

class AWSSupport(credentials: AWSCredentials)
  extends BotPlugin
  with ActorLogging {

  private val support = new AWSSupportClient(credentials)

  override protected def name: String = "aws-support"

  override protected def help: String =
    s"""
       |Allows accessing AWS support tickets.
     """.stripMargin

  private val CaseDetails = matchText("show aws case (\\d+).*")

  private val ListCases = matchText("list aws cases")

  override protected def receiveText: ReceiveText = {

    case ListCases() =>
      respondInFuture {
        msg =>
          val caseList = getAllCases.map(summary(_) + "\n").mkString("\n")
          msg.response(caseList)
      }


    case CaseDetails(caseId) =>
      respondInFuture {
        msg =>
          log.info(s"Looking for case $caseId")

          Try(getAllCases) match {
            case Success(cases) =>
              cases.find(_.getDisplayId == caseId) match {
                case None =>
                  msg.response("Not a known support case.")
                case Some(cse) =>
                  msg.response(summary(cse))
              }
            case Failure(e) if e.getMessage.contains("Invalid case ID:") =>
              msg.response(s"Invalid case ID: $caseId")
          }
      }
  }

  private def getAllCases: List[CaseDetails] = {
    val unresolved = support.describeCases(new DescribeCasesRequest()).getCases.asScala.toList
    val resolved = support.describeCases(new DescribeCasesRequest().withIncludeResolvedCases(true)).getCases.asScala.toList
    unresolved ++ resolved
  }

  private def summary(cse: CaseDetails): String =
    s"# ${cse.getDisplayId}: ${cse.getSubject}\n - submitted by: ${cse.getSubmittedBy}, status: ${cse.getStatus}"
}
