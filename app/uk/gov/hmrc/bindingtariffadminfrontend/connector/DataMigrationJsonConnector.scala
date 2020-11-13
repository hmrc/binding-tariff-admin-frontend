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

package uk.gov.hmrc.bindingtariffadminfrontend.connector

import javax.inject.{Inject, Singleton}
import play.api.libs.ws._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileUploadSubmission, FileUploaded}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class DataMigrationJsonConnector @Inject() (
  configuration: AppConfig,
  http: AuthenticatedHttpClient,
  wsClient: WSClient
) {

  def sendDataForProcessing(files: FileUploadSubmission)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    http.POST[FileUploadSubmission, HttpResponse](
      s"${configuration.dataMigrationUrl}/binding-tariff-data-transformation/send-data-for-processing",
      files
    )

  def getStatusOfJsonProcessing(implicit hc: HeaderCarrier): Future[HttpResponse] =
    http.GET[HttpResponse](s"${configuration.dataMigrationUrl}/binding-tariff-data-transformation/processing-status")

  def downloadBTIJson: Future[WSResponse] =
    wsClient
      .url(s"${configuration.dataMigrationUrl}/binding-tariff-data-transformation/transformed-bti-records")
      .withMethod("GET")
      .stream()

  def downloadLiabilitiesJson: Future[WSResponse] =
    wsClient
      .url(s"${configuration.dataMigrationUrl}/binding-tariff-data-transformation/transformed-liabilities-records")
      .withMethod("GET")
      .stream()

  def downloadMigrationReports: Future[WSResponse] =
    wsClient
      .url(s"${configuration.dataMigrationUrl}/binding-tariff-data-transformation/migration-reports")
      .withMethod("GET")
      .stream()

  def sendHistoricDataForProcessing(files: List[FileUploaded])(implicit hc: HeaderCarrier): Future[HttpResponse] =
    http.POST[List[FileUploaded], HttpResponse](
      s"${configuration.dataMigrationUrl}/binding-tariff-data-transformation/send-historic-data-for-processing",
      files
    )

  def getStatusOfHistoricDataProcessing(implicit hc: HeaderCarrier): Future[HttpResponse] =
    http.GET[HttpResponse](
      s"${configuration.dataMigrationUrl}/binding-tariff-data-transformation/historic-processing-status"
    )

  def downloadHistoricJson: Future[WSResponse] =
    wsClient
      .url(s"${configuration.dataMigrationUrl}/binding-tariff-data-transformation/historic-data")
      .withMethod("GET")
      .stream()

  def deleteHistoricData()(implicit hc: HeaderCarrier): Future[Unit] =
    http
      .DELETE[HttpResponse](s"${configuration.dataMigrationUrl}/binding-tariff-data-transformation/historic-data")
      .map(_ => ())
}
