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
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.connector.DataMigrationJsonConnector
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.UploadRequest
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http.{Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

@Singleton
class DataMigrationJsonController @Inject()(authenticatedAction: AuthenticatedAction,
                                            service: DataMigrationService,
                                            connector: DataMigrationJsonConnector,
                                            actorSystem: ActorSystem,
                                            override val messagesApi: MessagesApi,
                                            implicit val appConfig: AppConfig) extends FrontendController with I18nSupport {

  private lazy val form = Form[UploadRequest](
    mapping[UploadRequest, String, String](
      "filename" -> text,
      "mimetype" -> text
    )(UploadRequest.apply)(UploadRequest.unapply)
  )

  def sendDataForProcessing: Action[MultipartFormData[TemporaryFile]] = authenticatedAction.async(parse.multipartFormData) { implicit request =>

    form.bindFromRequest.fold(
      _ => successful(BadRequest),

      uploadRequest  => {
        val file = request.body.files.find(_.filename.nonEmpty)

        if (file.isDefined) {

          connector.sendDataForProcessing().map(_ => Accepted) recover handlingError
        } else {
          successful(BadRequest)
        }
      }
    )
  }

  def downLoadFiles: Action[AnyContent] = authenticatedAction.async { implicit request =>

    connector.getBtiJson().map { result =>
      Ok(Json.prettyPrint(result).replaceAll("_x000D_", "\\\\r")).withHeaders(
        "Content-Type" -> "application/json",
        "Content-Disposition" -> s"attachment; filename=BTI-Data-Migration${DateTime.now().toString("yyyyMMddHHmmss")}.json"
      )
    }
  }

  private def handlingError: PartialFunction[Throwable, Result] = {
    case e: Upstream4xxResponse => new Status(e.upstreamResponseCode)
    case _: Upstream5xxResponse => BadGateway
    case e: Throwable => InternalServerError(e.getMessage)
  }
}
