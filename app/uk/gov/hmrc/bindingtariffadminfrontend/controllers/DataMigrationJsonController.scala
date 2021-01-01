/*
 * Copyright 2021 HM Revenue & Customs
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

import java.time.LocalDate

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.csv.scaladsl.CsvParsing.lineScanner
import akka.stream.alpakka.csv.scaladsl.{ByteOrderMark, CsvFormatting, CsvQuotingStyle}
import akka.stream.scaladsl.{FileIO, Source}
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.libs.ws.WSResponse
import play.api.mvc._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.connector.DataTransformationConnector
import uk.gov.hmrc.bindingtariffadminfrontend.model.Anonymize
import uk.gov.hmrc.bindingtariffadminfrontend.views
import uk.gov.hmrc.bindingtariffadminfrontend.views.html.csv_processing_status
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

@Singleton
class DataMigrationJsonController @Inject() (
  authenticatedAction: AuthenticatedAction,
  connector: DataTransformationConnector,
  implicit val system: ActorSystem,
  implicit val materializer: Materializer,
  mcc: MessagesControllerComponents,
  override val messagesApi: MessagesApi,
  implicit val appConfig: AppConfig
) extends FrontendController(mcc)
    with I18nSupport {

  def getAnonymiseData: Action[AnyContent] = authenticatedAction.async { implicit request =>
    successful(Ok(views.html.file_anonymisation_upload()))
  }

  private def errorLog(filename: String) = s" ************ Error occurred while processing file $filename ************"

  // scalastyle:off method.length
  // scalastyle:off cyclomatic.complexity
  def anonymiseData: Action[MultipartFormData[TemporaryFile]] = authenticatedAction.async(parse.multipartFormData) {
    implicit request =>
      var headers: Option[List[String]] = None

      val file = request.body.files.find(_.filename.nonEmpty)
      file match {
        case Some(name) =>
          val csvData = FileIO
            .fromPath(name.ref.file.toPath())
            .zipWithIndex
            .map {
              case (file, chunkIndex) =>
                //This is done because the byte order mark (BOM) causes problems with first column header
                if (chunkIndex == 0L) {
                  if (file.startsWith(ByteOrderMark.UTF_8)) {
                    file.drop(ByteOrderMark.UTF_8.length).dropWhile(b => b.toChar.isWhitespace)
                  } else {
                    file.dropWhile(b => b.toChar.isWhitespace)
                  }
                } else {
                  file
                }
            }
            .via(lineScanner(maximumLineLength = Int.MaxValue))
            .log(errorLog(name.ref.file.getName))
            .map(_.map(_.utf8String))
            .filter(_.mkString.trim.nonEmpty) // ignore blank lines in CSV
            .map { list =>
              headers match {
                case None =>
                  headers = Some(list)
                  (list, None)
                case Some(headers) =>
                  (headers, Some(list))
              }
            }
            .zipWithIndex
            .map {
              case ((headers, None), _) =>
                (headers, None)

              case ((headers, Some(data)), rowIndex) =>
                val dataByColumn: Map[String, String] = ListMap(headers.zip(data): _*)

                val anonymized: Map[String, String] = Anonymize.anonymize(name.filename, dataByColumn)

                if (data.length != headers.length) {
                  Logger.error(
                    s"Row ${rowIndex + 1} did not have the expected number of columns: (${data.length}) instead of (${headers.length})"
                  )
                }

                (headers, Some(headers.map(col => anonymized(col))))
            }
            .flatMapConcat {
              case (headers, None) =>
                Source.single(headers).via(CsvFormatting.format(quotingStyle = CsvQuotingStyle.Always))
              case (_, Some(data)) =>
                Source.single(data).via(CsvFormatting.format(quotingStyle = CsvQuotingStyle.Always))
            }

          successful(
            Ok.chunked(csvData)
              .withHeaders(
                "Content-Type"        -> "application/json",
                "Content-Disposition" -> s"attachment; filename=${name.filename}"
              )
          )

        case None =>
          successful(BadRequest)
      }
  }

  def checkStatus: Action[AnyContent] = authenticatedAction.async { implicit request =>
    Future.successful(Ok(csv_processing_status()))
  }

  def getStatusOfJsonProcessing: Action[AnyContent] = authenticatedAction.async { implicit request =>
    connector.getStatusOfJsonProcessing.map {
      case response if response.status == OK => Ok(response.body).as("application/json")
      case response                          => Status(response.status)(response.body).as("application/json")
    }
  }

  def downloadBTIJson: Action[AnyContent] = authenticatedAction.async {
    downloadJson(connector.downloadBTIJson, "BTI")
  }

  def downloadLiabilitiesJson: Action[AnyContent] = authenticatedAction.async {
    downloadJson(connector.downloadLiabilitiesJson, "Liabilities")
  }

  def downloadCorrespondenceJson: Action[AnyContent] = authenticatedAction.async {
    downloadJson(connector.downloadCorrespondenceJson, "Correspondence")
  }

  def downloadMiscellaneousJson: Action[AnyContent] = authenticatedAction.async {
    downloadJson(connector.downloadMiscellaneousJson, "Miscellaneous")
  }

  def downloadMigrationReports: Action[AnyContent] = authenticatedAction.async {

    connector.downloadMigrationReports
      .map { res =>
        res.status match {
          case OK => res.bodyAsSource
          case _ =>
            throw new BadRequestException(
              s"Failed to get the archive from data migration api for the migration reports" + res.status
            )
        }
      }
      .map { dataContent =>
        Ok.chunked(dataContent)
          .withHeaders(
            "Content-Type"        -> "application/zip",
            "Content-Disposition" -> s"attachment; filename=Data-Migration-Reports-${DateTime.now().toString("ddMMyyyyHHmmss")}.zip"
          )
      }
  }

  private def downloadJson(download: Future[WSResponse], jsonType: String): Future[Result] =
    download
      .map { res =>
        res.status match {
          case OK => res.bodyAsSource
          case _ =>
            throw new BadRequestException(
              s"Failed to get mapped json from data migration api for $jsonType" + res.status
            )
        }
      }
      .map { dataContent =>
        Ok.chunked(dataContent)
          .withHeaders(
            "Content-Type"        -> "application/zip",
            "Content-Disposition" -> s"attachment; filename=$jsonType-Data-Migration-${DateTime.now().toString("ddMMyyyyHHmmss")}.zip"
          )
      }

}

case class ExtractionDateForm(extractionDate: LocalDate)
