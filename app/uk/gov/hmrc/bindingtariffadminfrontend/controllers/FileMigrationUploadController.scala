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

import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.UploadAttachmentRequest
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.bindingtariffadminfrontend.views
import uk.gov.hmrc.http.{Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

@Singleton
class FileMigrationUploadController @Inject()(
                                               authenticatedAction: AuthenticatedAction,
                                              service: DataMigrationService,
                                               mcc: MessagesControllerComponents,
                                               override val messagesApi: MessagesApi,
                                              implicit val appConfig: AppConfig
                                             ) extends FrontendController(mcc) with I18nSupport {

  private lazy val form = Form[UploadAttachmentRequest](
    mapping[UploadAttachmentRequest, String, String](
      "filename" -> text,
      "mimetype" -> text
    )(UploadAttachmentRequest.apply)(UploadAttachmentRequest.unapply)
  )

  def get: Action[AnyContent] = authenticatedAction.async { implicit request =>
    successful(Ok(views.html.file_migration_upload(form)))
  }

  def post: Action[MultipartFormData[TemporaryFile]] = authenticatedAction.async(parse.multipartFormData) { implicit request =>
    form.bindFromRequest.fold(
      _ => successful(BadRequest),

      uploadRequest  => {
        val file = request.body.files.find(_.filename.nonEmpty)
        if (file.isDefined) {
          service.upload(uploadRequest, file.get.ref).map(_ => Accepted) recover handlingError
        } else {
          successful(BadRequest)
        }
      }
    )
  }

  private def handlingError: PartialFunction[Throwable, Result] = {
    case e: Upstream4xxResponse => new Status(e.upstreamResponseCode)
    case _: Upstream5xxResponse => BadGateway
    case e: Throwable => InternalServerError(e.getMessage)
  }

}
