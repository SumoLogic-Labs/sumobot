package com.sumologic.sumobot.plugins.awssupport

import akka.actor.ActorLogging
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.support.AWSSupportClient
import com.amazonaws.services.support.model.{CaseDetails, DescribeCasesRequest}
import com.sumologic.sumobot.plugins.BotPlugin

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

class AWSSupport(credentials: Map[String, AWSCredentials])
  extends BotPlugin
  with ActorLogging {

  case class CaseInAccount(account: String, caseDetails: CaseDetails)

  private val clients = credentials.map(tpl => tpl._1 -> new AWSSupportClient(tpl._2))

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
          msg.message(caseList)
      }

    case CaseDetails(caseId) =>
      respondInFuture {
        msg =>
          log.info(s"Looking for case $caseId")

          Try(getAllCases) match {
            case Success(cases) =>
              cases.find(_.caseDetails.getDisplayId == caseId) match {
                case None =>
                  msg.response("Not a known support case.")
                case Some(cse) =>
                  msg.message(summary(cse))
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
    s"# ${cia.caseDetails.getDisplayId}: ${cia.caseDetails.getSubject}\n" +
      s" - account: ${cia.account}, submitted by: ${cia.caseDetails.getSubmittedBy}, status: ${cia.caseDetails.getStatus}"
}
