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

import java.io.File
import java.util.zip.{ZipFile, ZipEntry}

import javax.inject.{Inject, Singleton}
import org.apache.commons.io.FileUtils
import play.api.data.{Form, Forms}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigratableCase
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigratableCase.REST.format
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.bindingtariffadminfrontend.views
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import scala.util.{Failure, Success, Try}
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.JsonFraming
import akka.stream.scaladsl.Source
import akka.NotUsed
import scala.concurrent.Future
import akka.stream.IOResult
import akka.stream.Graph
import akka.stream.scaladsl.Flow
import scala.util.control.NonFatal
import akka.stream.scaladsl.StreamConverters
import play.api.mvc.MultipartFormData.FilePart
import akka.util.ByteString
import java.io.InputStream
import akka.stream.Attributes
import akka.event.Logging.LogLevel
import akka.stream.scaladsl.Framing.FramingException
import play.api.data.validation.ValidationError
import akka.Done

@Singleton
class CaseMigrationUploadController @Inject()(authenticatedAction: AuthenticatedAction,
                                              service: DataMigrationService,
                                              override val messagesApi: MessagesApi,
                                              implicit val appConfig: AppConfig) extends FrontendController with I18nSupport {

  private lazy val form = Form("file" -> Forms.text)

  def get: Action[AnyContent] = authenticatedAction.async { implicit request =>
    successful(Ok(views.html.case_migration_upload(form)))
  }

  def post: Action[MultipartFormData[TemporaryFile]] = authenticatedAction.async(parse.multipartFormData) { implicit request =>
    val priority: Boolean = request.body.dataParts.get("priority").exists(_.head.toBoolean)
    request.body.file("file").filter(_.filename.nonEmpty) match {
      case None =>
        successful(Redirect(routes.CaseMigrationUploadController.get()))
      case Some(part: FilePart[TemporaryFile]) =>
        service.prepareMigration(toJsonSource(part.ref.file, part.contentType), priority)
          .map(_ => Redirect(routes.DataMigrationStateController.get()))
          .recoverWith {
            // Happens when Akka doesn't know how to split the file into JSON chunks
            case framing: FramingException =>
              val indexZero = JsPath.apply(0)
              val validationErrors = Seq(ValidationError(Seq(framing.getMessage())))
              val jsonErrors = Seq(indexZero -> validationErrors)
              successful(Ok(views.html.case_migration_file_error(jsonErrors)))
            // Happens when parsing JSON chunks with Play
            case JsResultException(errs) =>
              successful(Ok(views.html.case_migration_file_error(errs)))
          }
    }
  }

  private def toJsonSource(file: File, contentType: Option[String]): Source[MigratableCase, _] = {
    val dataSource: Source[ByteString, _] = 
      if (contentType.map(_ == "application/zip").getOrElse(false)) {
        val zipFile = new ZipFile(file)
        val zipEntries = zipFile.entries()

        if (zipEntries.hasMoreElements())
          StreamConverters.fromInputStream(() => zipFile.getInputStream(zipEntries.nextElement))
        else
          Source.empty[ByteString]

      } else {
        FileIO.fromPath(file.toPath())
      }

    dataSource
      .via(JsonFraming.objectScanner(Int.MaxValue))
      .map(_.utf8String)
      .mapAsync(1) { str =>
        Future.fromTry {
          for {
            json <- Try { Json.parse(str) }
            // TODO: Use JsResult.toTry once we upgrade to Play 2.6
            cse <- Json.fromJson[MigratableCase](json) match {
              case JsSuccess(cse, _) => Success(cse)
              case JsError(errors) => Failure(JsResultException(errors))
            }
          } yield cse
        }
      }
  }
}
