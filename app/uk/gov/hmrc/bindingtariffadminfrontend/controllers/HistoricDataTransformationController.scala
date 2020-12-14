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
import org.joda.time.DateTime
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.ws.WSResponse
import play.api.mvc._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.connector.DataTransformationConnector
import uk.gov.hmrc.bindingtariffadminfrontend.views
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class HistoricDataTransformationController @Inject() (
  authenticatedAction: AuthenticatedAction,
  connector: DataTransformationConnector,
  mcc: MessagesControllerComponents,
  override val messagesApi: MessagesApi,
  implicit val appConfig: AppConfig
) extends FrontendController(mcc)
    with I18nSupport {

  def get: Action[AnyContent] = authenticatedAction.async { implicit request =>
    for {
      stats <- connector.getHistoricTransformationStatistics
    } yield Ok(views.html.historic_data_transformation(stats))
  }

  def status: Action[AnyContent] = authenticatedAction.async { implicit request =>
    Future.successful(Ok(views.html.historic_data_transformation_status()))
  }

  def initiate: Action[AnyContent] = authenticatedAction.async { implicit request =>
    for {
      result <- connector.initiateHistoricTransformation
    } yield {
      result.status match {
        case ACCEPTED => Redirect(routes.HistoricDataTransformationController.status())
        case _        => throw new RuntimeException("data transformation error")
      }
    }
  }

  def getStatusOfHistoricDataTransformation: Action[AnyContent] = authenticatedAction.async { implicit request =>
    connector.getStatusOfHistoricDataTransformation.map {
      case response if response.status == OK => Ok(response.body).as("application/json")
      case response                          => Status(response.status)(response.body).as("application/json")
    }
  }

  def downloadTransformedJson: Action[AnyContent] = authenticatedAction.async { implicit request =>
    downloadData(connector.downloadTransformedHistoricData)
  }

  private def downloadData(download: Future[WSResponse]): Future[Result] =
    download
      .map { res =>
        res.status match {
          case OK => res.bodyAsSource
          case _ =>
            throw new BadRequestException(
              s"Failed to get transformed historic data from data migration api " + res.status
            )
        }
      }
      .map { dataContent =>
        Ok.chunked(dataContent)
          .withHeaders(
            "Content-Type"        -> "application/zip",
            "Content-Disposition" -> s"attachment; filename=Transformed-Historic-Data-${DateTime.now().toString("ddMMyyyyHHmmss")}.zip"
          )
      }
}
