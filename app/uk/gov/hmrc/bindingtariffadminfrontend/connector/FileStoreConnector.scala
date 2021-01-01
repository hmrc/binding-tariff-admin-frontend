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

package uk.gov.hmrc.bindingtariffadminfrontend.connector

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.{Paged, Pagination}
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileSearch, FileUploaded, UploadRequest, UploadTemplate}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class FileStoreConnector @Inject() (configuration: AppConfig, http: AuthenticatedHttpClient) {

  def delete()(implicit hc: HeaderCarrier): Future[Unit] =
    http.DELETE[HttpResponse](s"${configuration.filestoreUrl}/file").map(_ => ())

  def delete(id: String)(implicit hc: HeaderCarrier): Future[Unit] =
    http.DELETE[HttpResponse](s"${configuration.filestoreUrl}/file/$id").map(_ => ())

  def find(id: String)(implicit hc: HeaderCarrier): Future[Option[FileUploaded]] =
    http.GET[Option[FileUploaded]](s"${configuration.filestoreUrl}/file/$id")

  def find(search: FileSearch, pagination: Pagination)(implicit hc: HeaderCarrier): Future[Paged[FileUploaded]] = {
    val queryParams = FileSearch.bindable.unbind("", search) + "&" + Pagination.bindable.unbind("", pagination)
    http.GET[Paged[FileUploaded]](s"${configuration.filestoreUrl}/file?$queryParams")
  }

  def initiate(file: UploadRequest)(implicit hc: HeaderCarrier): Future[UploadTemplate] =
    http.POST[UploadRequest, UploadTemplate](s"${configuration.filestoreUrl}/file", file)

  def publish(id: String)(implicit hc: HeaderCarrier): Future[FileUploaded] =
    http.POSTEmpty[FileUploaded](s"${configuration.filestoreUrl}/file/$id/publish")
}
