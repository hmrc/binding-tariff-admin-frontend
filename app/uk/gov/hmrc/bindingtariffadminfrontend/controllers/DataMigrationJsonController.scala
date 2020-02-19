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

import java.nio.charset.{Charset, StandardCharsets}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet}
import akka.stream.alpakka.csv.impl.{CsvToMapAsStringsStage, CsvToMapStage, CsvToMapStageBase}
import akka.stream.alpakka.csv.scaladsl.CsvParsing.lineScanner
import akka.stream.alpakka.csv.scaladsl.CsvToMap.toMap
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json._
import play.api.libs.streams.Accumulator
import play.api.mvc._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.connector.DataMigrationJsonConnector
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.UploadRequest
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.collection.immutable.ListMap
import scala.collection.{SortedMap, immutable}
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class DataMigrationJsonController @Inject()(authenticatedAction: AuthenticatedAction,
                                            service: DataMigrationService,
                                            connector: DataMigrationJsonConnector,
                                            implicit val system: ActorSystem,
                                            implicit val materializer: Materializer,
                                            override val messagesApi: MessagesApi,
                                            implicit val appConfig: AppConfig) extends FrontendController with I18nSupport {

  private lazy val form = Form[UploadRequest](
    mapping[UploadRequest, String, String](
      "filename" -> text,
      "mimetype" -> text
    )(UploadRequest.apply)(UploadRequest.unapply)
  )

  def randomize(s: String): String = {

    def r(s: List[Char], n: Int): List[Char] = {
      if (n == 0) Nil
      else {
        val i = Math.floor(Math.random() * s.length).toInt
        s(i) :: r(s, n - 1)
      }
    }

    r(s.toList, s.length).mkString
  }

  def anonymiseData = EssentialAction { requestHeader =>

    val keys = List("FirstName", "LastName", "ContactName", "CaseEmail", "Contact", "CancelledUser",
      "Name", "Address1", "Address2", "Address3", "TelephoneNo", "FaxNo", "Email", "City", "VATRegTurnNo", "Signature")

    var headers: Option[List[String]] = None

    Accumulator.source.map { source =>

      Ok.chunked {
        source
          .via(lineScanner())
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
                case ("Postcode ", _ ) => ("Postcode ", "ZZ11ZZ")
                case x => x
              }
              (headers, Some(listMap.values.toList))
          }
          .map {
            case (headers, None) =>
              ByteString(headers.map(_.replace("\"", "\\\"")).map(s => '"' + s + '"').mkString(",") + "\n")
            case (headers, Some(data)) =>
              ByteString(data.map(_.replace("\"", "\\\"")).map(s => '"' + s + '"').mkString(",") + "\n")
          }
      }
    }
  }

  def get: Action[AnyContent] = authenticatedAction.async { implicit request =>

    val res = for {
      files <- service.getDataMigrationFilesDetails(List("tblCaseClassMeth_csv", "historicCases_csv", "eBTI_Application_csv",
        "eBTI_Addresses_csv", "tblCaseRecord_csv", "tblCaseBTI_csv", "tblImages_csv",
        "tblMovement_csv", "tblSample_csv", "tblUser_csv"))
      result <- connector.generateJson(files)
    } yield {
      Json.prettyPrint(result).replaceAll("_x000D_", "\\\\r")
    }

    res.map { result =>
      Ok(result).withHeaders(
        "Content-Type" -> "application/json",
        "Content-Disposition" -> s"attachment; filename=BTI-Data-Migration${DateTime.now().toString("yyyyMMddHHmmss")}.json"
      )
    }
  }
}
