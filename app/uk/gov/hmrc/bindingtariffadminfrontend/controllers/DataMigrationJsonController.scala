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
import akka.stream.alpakka.csv.scaladsl.CsvParsing.lineScanner
import akka.stream.alpakka.csv.scaladsl.CsvToMap.toMap
import akka.stream.scaladsl.{FileIO, Framing, Sink, Source}
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files
import play.api.libs.Files.TemporaryFile
import play.api.libs.streams.Accumulator
import play.api.mvc._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.connector.DataMigrationJsonConnector
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.UploadRequest
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.tools.nsc.io.File
import scala.util.{Failure, Random, Success}

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

  def verbatimBodyParser: BodyParser[Source[ByteString, _]] = BodyParser { j =>
    // Return the source directly. We need to return
    // an Accumulator[Either[Result, T]], so if we were
    // handling any errors we could map to something like
    // a Left(BadRequest("error")). Since we're not
    // we just wrap the source in a Right(...)
    Accumulator.source[ByteString]
      .map(Right.apply)
  }

  def randomize(s: List[Char], n: Int): List[Char] = {
    if(n==0) Nil
    else {
      val i = Math.floor(Math.random() * s.length).toInt
      s(i) :: randomize(s, n - 1)
    }
  }

  def uploadCSV = Action(verbatimBodyParser) { implicit request =>

      val source = request.body
        .map { x =>
          println("Here1")
          println(x.utf8String)
          x
        }
        .via(Framing.delimiter(ByteString("\n"), 4096))
        .map { x =>
          println("Here2")
          println(x.utf8String)
          x
        }
        .via(lineScanner())
        .via(toMap())
        .map(_.mapValues(_.utf8String))
        .map { record =>
          println(record)
          record.map {
            case ("col2", v1 ) => ("col2", randomize(v1.toList, v1.toList.size).mkString(""))
          }
        }.map { x =>
          x.values.map(s => '"'+s.replaceAll("""["]""", """\"""")+'"').mkString(",")
        }
    Ok.chunked(source)
  }


//    form.bindFromRequest.fold(
//      _ => {
//        successful(BadRequest)
//      },
//
//      uploadRequest  => {
//        request.body.files.find(_.filename.nonEmpty) match {
//          case Some(file) =>
//            val source = Source[MultipartFormData.FilePart[Files.TemporaryFile]](request.body.files.toList).flatMapMerge( 16, file => {
//              FileIO.fromPath(file.ref.file.toPath)
//                .via(lineScanner(escapeChar = '\u0001'))
//                .via(toMap())
//                .map(_.mapValues(_.utf8String))
//                .grouped(32)
//                .map { map =>
//                  (file, map)
//                }
//            })
//            .mapAsync(16) { case (file, record) =>
//              record.map{ x =>
//                x()
//
//
//              }
//            }
//
//            successful(Ok.chunked(source))
//
//          case None => successful(BadRequest)
//        }
//      }
//    )
//  }

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
