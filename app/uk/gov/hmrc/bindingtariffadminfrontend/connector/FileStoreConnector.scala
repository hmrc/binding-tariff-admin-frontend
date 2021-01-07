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

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.{Paged, Pagination}
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileSearch, FileUploaded, UploadRequest, UploadTemplate}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileStoreConnector @Inject() (configuration: AppConfig, http: AuthenticatedHttpClient)(
  implicit mat: Materializer
) {

  implicit val ec: ExecutionContext = mat.executionContext

  private lazy val ParamLength = 42 // A 36-char UUID plus &id= and some wiggle room
  private lazy val BatchSize =
    ((configuration.maxUriLength - configuration.filestoreUrl.length) / ParamLength).intValue()

  def delete()(implicit hc: HeaderCarrier): Future[Unit] =
    http.DELETE[HttpResponse](s"${configuration.filestoreUrl}/file").map(_ => ())

  def delete(id: String)(implicit hc: HeaderCarrier): Future[Unit] =
    http.DELETE[HttpResponse](s"${configuration.filestoreUrl}/file/$id").map(_ => ())

  def find(id: String)(implicit hc: HeaderCarrier): Future[Option[FileUploaded]] =
    http.GET[Option[FileUploaded]](s"${configuration.filestoreUrl}/file/$id")

  def find(search: FileSearch, pagination: Pagination)(implicit hc: HeaderCarrier): Future[Paged[FileUploaded]] =
    if (search.ids.exists(_.nonEmpty) && pagination.equals(Pagination.max)) {
      Source(search.ids.get)
        .grouped(BatchSize)
        .mapAsyncUnordered(Runtime.getRuntime.availableProcessors()) { idBatch =>
          http.GET[Paged[FileUploaded]](findQueryUri(search.copy(ids = Some(idBatch.toSet)), Pagination.max))
        }
        .runFold(Seq.empty[FileUploaded]) {
          case (acc, next) => acc ++ next.results
        }
        .map(results => Paged(results = results, pagination = Pagination.max, resultCount = results.size))
    } else {
      http.GET[Paged[FileUploaded]](findQueryUri(search, pagination))
    }

  def initiate(file: UploadRequest)(implicit hc: HeaderCarrier): Future[UploadTemplate] =
    http.POST[UploadRequest, UploadTemplate](s"${configuration.filestoreUrl}/file", file)

  def publish(id: String)(implicit hc: HeaderCarrier): Future[FileUploaded] =
    http.POSTEmpty[FileUploaded](s"${configuration.filestoreUrl}/file/$id/publish")

  private def findQueryUri(search: FileSearch, pagination: Pagination): String = {
    val queryParams = FileSearch.bindable.unbind("", search) + "&" + Pagination.bindable.unbind("", pagination)
    s"${configuration.filestoreUrl}/file?$queryParams"
  }
}
