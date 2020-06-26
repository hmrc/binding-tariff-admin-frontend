/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.bindingtariffadminfrontend.controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.csv.scaladsl.{CsvFormatting, CsvQuotingStyle}
//import akka.stream.alpakka.csv.scaladsl.CsvParsing.lineScanner
import uk.gov.hmrc.bindingtariffadminfrontend.akka_fix.csv.CsvParsing.lineScanner
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.libs.ws.StreamedResponse
import play.api.mvc._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.connector.DataMigrationJsonConnector
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.bindingtariffadminfrontend.views
import uk.gov.hmrc.bindingtariffadminfrontend.views.html.csv_processing_status
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

@Singleton
class DataMigrationJsonController @Inject()(authenticatedAction: AuthenticatedAction,
                                            service: DataMigrationService,
                                            connector: DataMigrationJsonConnector,
                                            implicit val system: ActorSystem,
                                            implicit val materializer: Materializer,
                                            override val messagesApi: MessagesApi,
                                            implicit val appConfig: AppConfig) extends FrontendController with I18nSupport {

  private lazy val keys = List("FirstName", "LastName", "ContactName", "CaseEmail", "Contact", "CancelledUser",
    "Name", "Address1", "Address2", "Address3", "TelephoneNo", "FaxNo", "Email", "City", "VATRegTurnNo", "Signature",
    "CaseName", "CaseAddress1", "CaseAddress2", "CaseAddress3", "CaseAddress4", "CaseAddress5", "CasePostCode",
    "CaseTelephoneNo", "CaseFaxNo", "CaseAgentName", "CaseNameCompleted", "LiabilityPortOfficerName", "LiabilityPortOfficerTel",
    "SupressUserName", "InsBoardFileUserName")

  private def randomize(s: String): String = {

    def r(s: List[Char], n: Int): List[Char] = {
      if (n == 0) Nil
      else {
        val i = Math.floor(Math.random() * s.length).toInt
        s(i) :: r(s, n - 1)
      }
    }

    r(s.toList, s.length).mkString
  }


  def getAnonymiseData: Action[AnyContent] = authenticatedAction.async { implicit request =>
    successful(Ok(views.html.file_anonymisation_upload()))
  }

  private def errorLog(filename:String) = s" ************ Error occurred while processing file $filename ************"

  // scalastyle:off method.length
  // scalastyle:off cyclomatic.complexity
  def anonymiseData: Action[MultipartFormData[TemporaryFile]] = authenticatedAction.async(parse.multipartFormData) { implicit request =>

    var headers: Option[List[String]] = None

    val file = request.body.files.find(_.filename.nonEmpty)
    file match {
      case Some(name) =>
        val res = FileIO.fromPath(name.ref.file.toPath())
          .via(lineScanner()).log(errorLog(name.ref.file.getName))
          .map(_.map(_.utf8String))
          .map { list =>
            headers match {
              case None =>
                headers = Some(list)
                (list, None)
              case Some(headers) =>
                (headers, Some(list))
            }
          }
          .map {
            case (headers, None) =>
              (headers, None)
            case (headers, Some(data)) =>
              val listMap = ListMap(headers.zip(data): _*) map {
                case (key, v1) if keys.contains(key) => (key, randomize(v1))
                case ("Postcode ", _) => ("Postcode ", "ZZ11ZZ")
                case x => x
              }
              (headers, Some(listMap.values.toList))
          }
          .flatMapMerge(1, {
            case (headers, None) =>
              Source.single(headers).via(CsvFormatting.format(quotingStyle = CsvQuotingStyle.Always))
            case (_, Some(data)) =>
              Source.single(data).via(CsvFormatting.format(quotingStyle = CsvQuotingStyle.Always))
          })

        successful(Ok.chunked(res).withHeaders(
          "Content-Type" -> "application/json",
          "Content-Disposition" -> s"attachment; filename=${name.filename}"))
      case None => successful(BadRequest)
    }
  }

  def postDataAndRedirect: Action[AnyContent] = authenticatedAction.async { implicit request =>

    for {
      files <- service.getDataMigrationFilesDetails(List("tblCaseClassMeth_csv", "historicCases_csv", "eBTI_Application_csv",
        "eBTI_Addresses_csv", "tblCaseRecord_csv", "tblCaseBTI_csv", "tblImages_csv",
        "tblMovement_csv", "tblSample_csv", "tblUser_csv"))
      result <- connector.sendDataForProcessing(files)
    } yield {
      result.status match {
        case ACCEPTED => Redirect(routes.DataMigrationJsonController.checkStatus())
        case _ => throw new RuntimeException("data processing error")
      }
    }
  }

  def checkStatus: Action[AnyContent] = authenticatedAction.async { implicit request =>
    Future.successful(Ok(csv_processing_status()))
  }

  def getStatusOfJsonProcessing: Action[AnyContent] = authenticatedAction.async { implicit request =>
    connector.getStatusOfJsonProcessing.map{
      case response if response.status == OK => Ok(response.body).as("application/json")
      case response => Status(response.status)(response.body).as("application/json")
    }
  }

  def downloadBTIJson: Action[AnyContent] = authenticatedAction.async {

    downloadJson(connector.downloadBTIJson, "BTI")
  }

  def downloadLiabilitiesJson: Action[AnyContent] = authenticatedAction.async {

    downloadJson(connector.downloadLiabilitiesJson, "Liabilities")
  }

  private def downloadJson(download : Future[StreamedResponse], jsonType : String): Future[Result] ={
    download.map{ res =>
      res.headers.status match{
        case OK => res.body
        case _ => throw new BadRequestException(s"Failed to get mapped json from data migration api for $jsonType" + res.headers.status)
      }
    }
    .map{ dataContent =>
      Ok.chunked(dataContent).withHeaders(
        "Content-Type" -> "application/zip",
        "Content-Disposition" -> s"attachment; filename=$jsonType-Data-Migration${DateTime.now().toString("ddMMyyyyHHmmss")}.zip")
    }
  }

}
