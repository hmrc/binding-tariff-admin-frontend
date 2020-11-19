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

import java.util.UUID

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.libs.ws.WSResponse
import play.api.mvc._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.connector.DataMigrationJsonConnector
import uk.gov.hmrc.bindingtariffadminfrontend.forms.{InitiateHistoricDataFormProvider, UploadHistoricDataFormProvider}
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.bindingtariffadminfrontend.views
import uk.gov.hmrc.http.{BadRequestException, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

@Singleton
class HistoricDataUploadController @Inject() (
  authenticatedAction: AuthenticatedAction,
  service: DataMigrationService,
  connector: DataMigrationJsonConnector,
  uploadHistoricDataFormProvider: UploadHistoricDataFormProvider,
  initiateHistoricDataFormProvider: InitiateHistoricDataFormProvider,
  mcc: MessagesControllerComponents,
  override val messagesApi: MessagesApi,
  implicit val appConfig: AppConfig
) extends FrontendController(mcc)
    with I18nSupport {
  private lazy val uploadForm   = uploadHistoricDataFormProvider()
  private lazy val initiateForm = initiateHistoricDataFormProvider()

  def get: Action[AnyContent] = authenticatedAction.async { implicit request =>
    successful(Ok(views.html.historic_data_upload(batchId = UUID.randomUUID().toString)))
  }

  def checkHistoricStatus: Action[AnyContent] = authenticatedAction.async { implicit request =>
    successful(Ok(views.html.historic_data_upload_status()))
  }

  def getStatusOfHistoricDataProcessing: Action[AnyContent] = authenticatedAction.async { implicit request =>
    connector.getStatusOfHistoricDataProcessing.map {
      case response if response.status == OK => Ok(response.body).as("application/json")
      case response                          => Status(response.status)(response.body).as("application/json")
    }
  }

  def downloadHistoricJson: Action[AnyContent] = authenticatedAction.async { implicit request =>
    downloadJson(connector.downloadHistoricJson)
  }

  def post: Action[MultipartFormData[TemporaryFile]] = authenticatedAction.async(parse.multipartFormData) {
    implicit request =>
      uploadForm.bindFromRequest.fold(
        _ => successful(BadRequest),
        uploadRequest => {
          val file = request.body.files.find(_.filename.nonEmpty)
          if (file.isDefined) {
            service.upload(uploadRequest, file.get.ref).map(_ => Accepted) recover handlingError
          } else {
            successful(BadRequest)
          }
        }
      )
  }

  def initiateProcessing: Action[AnyContent] = authenticatedAction.async { implicit request =>
    initiateForm.bindFromRequest.fold(
      _ => successful(BadRequest),
      initiate =>
        for {
          files  <- service.getUploadedBatch(initiate.batchId)
          result <- connector.sendHistoricDataForProcessing(files)
        } yield {
          result.status match {
            case ACCEPTED => Redirect(routes.HistoricDataUploadController.checkHistoricStatus())
            case _        => throw new RuntimeException("data processing error")
          }
        }
    )

  }

  private def handlingError: PartialFunction[Throwable, Result] = {
    case e: Upstream4xxResponse => new Status(e.upstreamResponseCode)
    case _: Upstream5xxResponse => BadGateway
    case e: Throwable           => InternalServerError(e.getMessage)
  }

  private def downloadJson(download: Future[WSResponse]): Future[Result] =
    download
      .map { res =>
        res.status match {
          case OK => res.bodyAsSource
          case _  => throw new BadRequestException(s"Failed to get historic json from data migration api " + res.status)
        }
      }
      .map { dataContent =>
        Ok.chunked(dataContent)
          .withHeaders(
            "Content-Type"        -> "application/zip",
            "Content-Disposition" -> s"attachment; filename=Historic-Data-${DateTime.now().toString("ddMMyyyyHHmmss")}.zip"
          )
      }
}
