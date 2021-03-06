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

import java.io.File
import java.util.zip._

import akka.stream.scaladsl.Framing.FramingException
import akka.stream.scaladsl.{FileIO, JsonFraming, Source, StreamConverters}
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import play.api.data.{Form, Forms}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json._
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigratableCase
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigratableCase.REST.format
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.bindingtariffadminfrontend.views
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.util._
import com.fasterxml.jackson.core.JsonParseException

import scala.util.control.NonFatal

@Singleton
class CaseMigrationUploadController @Inject() (
  authenticatedAction: AuthenticatedAction,
  service: DataMigrationService,
  mcc: MessagesControllerComponents,
  override val messagesApi: MessagesApi,
  implicit val appConfig: AppConfig
) extends FrontendController(mcc)
    with I18nSupport {

  private lazy val form = Form("file" -> Forms.text)

  def get: Action[AnyContent] = authenticatedAction.async { implicit request =>
    successful(Ok(views.html.case_migration_upload(form)))
  }

  def post: Action[MultipartFormData[TemporaryFile]] = authenticatedAction.async(parse.multipartFormData) {
    implicit request =>
      val priority: Boolean = request.body.dataParts.get("priority").exists(_.head.toBoolean)
      request.body.file("file").filter(_.filename.nonEmpty) match {
        case None =>
          successful(Redirect(routes.CaseMigrationUploadController.get()))
        case Some(part: FilePart[TemporaryFile]) =>
          service
            .prepareMigration(toJsonSource(part.ref.path.toFile, part.contentType), priority)
            .map(_ => Redirect(routes.DataMigrationStateController.get()))
            .recoverWith {
              // Happens when Akka doesn't know how to split the file into JSON chunks
              case framing: FramingException =>
                val indexZero        = JsPath(0)
                val validationErrors = Seq(JsonValidationError(Seq(framing.getMessage)))
                val jsonErrors       = Seq(indexZero -> validationErrors)
                successful(Ok(views.html.case_migration_file_error(jsonErrors)))
              // Happens when parsing JSON with Jackson
              case parseException: JsonParseException =>
                val indexZero        = JsPath(0)
                val validationErrors = Seq(JsonValidationError(Seq(parseException.getMessage)))
                val jsonErrors       = Seq(indexZero -> validationErrors)
                successful(Ok(views.html.case_migration_file_error(jsonErrors)))
              // Happens when converting JSON into objects with Play
              case JsResultException(errs) =>
                successful(Ok(views.html.case_migration_file_error(errs)))
            }
      }
  }

  private def toJsonSource(file: File, contentType: Option[String]): Source[MigratableCase, _] = {
    val dataSource: Source[ByteString, _] =
      try {
        val zipFile    = new ZipFile(file)
        val zipEntries = zipFile.entries()

        if (zipEntries.hasMoreElements) {
          StreamConverters.fromInputStream(() => zipFile.getInputStream(zipEntries.nextElement))
        } else {
          Source.empty[ByteString]
        }

      } catch {
        case NonFatal(_) =>
          FileIO.fromPath(file.toPath)
      }

    dataSource
      .via(JsonFraming.objectScanner(Int.MaxValue))
      .map(_.utf8String)
      .mapAsync(1) { str =>
        Future.fromTry {
          for {
            json <- Try(Json.parse(str))
            // TODO: Use JsResult.toTry once we upgrade to Play 2.6
            cse <- Json.fromJson[MigratableCase](json) match {
                    case JsSuccess(cse, _) => Success(cse)
                    case JsError(errors)   => Failure(JsResultException(errors))
                  }
          } yield cse
        }
      }
  }
}
