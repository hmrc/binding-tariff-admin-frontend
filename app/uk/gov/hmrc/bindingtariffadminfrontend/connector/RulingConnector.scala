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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileUploaded, UploadRequest, UploadTemplate}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class RulingConnector @Inject()(configuration: AppConfig,
                                http: HttpClient) {

  def notify(id: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POSTEmpty(s"${configuration.rulingUrl}/ruling/$id/notify").map(_ => ())
  }

  def delete()(implicit hc: HeaderCarrier): Future[Unit] = {
    http.DELETE(s"${configuration.rulingUrl}/ruling").map(_ => ())
  }

}