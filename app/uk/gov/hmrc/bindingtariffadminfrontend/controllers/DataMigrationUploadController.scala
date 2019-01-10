/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import org.apache.commons.io.FileUtils
import play.api.data.{Form, Forms}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.Case
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.bindingtariffadminfrontend.views
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future.successful

@Singleton
class DataMigrationUploadController @Inject()(service: DataMigrationService,
                                              val messagesApi: MessagesApi,
                                              implicit val appConfig: AppConfig) extends FrontendController with I18nSupport {

  private def form = Form("file" -> Forms.text)

  def get: Action[AnyContent] = Action.async { implicit request =>
    successful(Ok(views.html.data_migration_upload(form)))
  }

  def post: Action[MultipartFormData[TemporaryFile]] = Action.async(parse.multipartFormData) { implicit request =>
    request.body.file("file").filter(_.filename.nonEmpty).map(_.ref.file) match {
      case Some(file: File) =>
        val result = toJson(file)
        result match {
          case JsSuccess(cases, _) =>
            successful(Redirect(routes.DataMigrationStateController.get()))
          case JsError(errs) =>
            successful(Ok(views.html.data_migration_file_error(errs)))
        }
      case None =>
        successful(Ok(views.html.data_migration_upload(form)))
    }
  }

  private def toJson(file: File): JsResult[Seq[Case]] = {
    val jsonValue: JsValue = Json.parse(FileUtils.readFileToString(file))
    Json.fromJson[Seq[Case]](jsonValue)
  }

}
