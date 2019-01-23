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

package uk.gov.hmrc.bindingtariffadminfrontend.connector

import java.io.FileNotFoundException
import java.net.{MalformedURLException, URL}

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import org.apache.commons.io.FileUtils
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.FilePart
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.FileUploaded
import uk.gov.hmrc.bindingtariffadminfrontend.model.{MigratableAttachment, MigrationFailedException}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class FileStoreConnector @Inject()(configuration: AppConfig, client: WSClient, http: HttpClient) {

  def delete(id: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.DELETE(s"${configuration.filestoreUrl}/file/$id").map(_ => Unit)
  }

  def upload(file: MigratableAttachment): Future[FileUploaded] = {
    val tmp = TemporaryFile(file.name, "")
    try {
      FileUtils.copyURLToFile(new URL(file.url), tmp.file)
      val filePart: MultipartFormData.Part[Source[ByteString, Future[IOResult]]] = FilePart(
        "file",
        file.name,
        Some(file.mimeType),
        FileIO.fromPath(tmp.file.toPath)
      )

      client.url(s"${configuration.filestoreUrl}/file")
        .post(Source(List(filePart)))
        .map(response => Json.fromJson[FileUploaded](Json.parse(response.body)).get)
    } catch {
      case _: FileNotFoundException => Future.failed(new MigrationFailedException(s"File didnt exist at [${file.url}]"))
      case _: MalformedURLException => Future.failed(new MigrationFailedException(s"File had invalid URL [${file.url}]"))
      case t: Throwable => Future.failed(new MigrationFailedException(s"File was inaccessible [${file.url}] due to [${t.getMessage}]"))
    }
  }

  def publish(id: String)(implicit hc: HeaderCarrier): Future[FileUploaded] = {
    http.POSTEmpty[FileUploaded](s"${configuration.filestoreUrl}/file/$id/publish")
  }

}
