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

package uk.gov.hmrc.bindingtariffadminfrontend.model

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.UploadRequest
import uk.gov.hmrc.play.json.Union

abstract class Upload extends UploadRequest {
  def batchId: String
}

sealed case class AttachmentUpload(
  override val fileName: String,
  override val mimeType: String,
  override val id: String,
  override val batchId: String
) extends Upload {
  override val publishable: Boolean = true
}

sealed case class MigrationDataUpload(
  override val fileName: String,
  override val mimeType: String,
  override val id: String,
  override val batchId: String
) extends Upload {
  override val publishable: Boolean = false
}

sealed case class HistoricDataUpload(
  override val fileName: String,
  override val mimeType: String,
  override val id: String,
  override val batchId: String
) extends Upload {
  override val publishable: Boolean = false
}

object Upload {
  implicit val attachmentFormat: Format[AttachmentUpload]   = Json.format[AttachmentUpload]
  implicit val migrationFormat: Format[MigrationDataUpload] = Json.format[MigrationDataUpload]
  implicit val historicFormat: Format[HistoricDataUpload]   = Json.format[HistoricDataUpload]

  implicit val format: Format[Upload] = Union
    .from[Upload]("type")
    .and[AttachmentUpload](classOf[AttachmentUpload].getSimpleName)
    .and[MigrationDataUpload](classOf[MigrationDataUpload].getSimpleName)
    .and[HistoricDataUpload](classOf[HistoricDataUpload].getSimpleName)
    .format
}
