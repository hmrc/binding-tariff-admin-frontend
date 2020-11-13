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

package uk.gov.hmrc.bindingtariffadminfrontend.model.filestore

import play.api.libs.json._
import uk.gov.hmrc.bindingtariffadminfrontend.util.FilenameUtil
import uk.gov.hmrc.play.json.Union

sealed abstract class UploadRequest(val fileName: String, val mimeType: String)

case class UploadAttachmentRequest(
  override val fileName: String,
  override val mimeType: String
) extends UploadRequest(fileName, mimeType)

case class UploadMigrationDataRequest(
  override val fileName: String,
  override val mimeType: String
) extends UploadRequest(fileName, mimeType)

case class UploadHistoricDataRequest(
  override val fileName: String,
  override val mimeType: String
) extends UploadRequest(fileName, mimeType)

object UploadRequest {
  val Attachment    = classOf[UploadAttachmentRequest].getSimpleName()
  val MigrationData = classOf[UploadMigrationDataRequest].getSimpleName()
  val HistoricData  = classOf[UploadHistoricDataRequest].getSimpleName()

  val attachmentWrites: Writes[UploadAttachmentRequest] = Writes(upload =>
    Json.obj(
      "id"       -> FilenameUtil.toID(upload.fileName),
      "fileName" -> upload.fileName,
      "mimeType" -> upload.mimeType
    )
  )

  implicit val attachmentFormat: Format[UploadAttachmentRequest] =
    Format(Json.reads[UploadAttachmentRequest], attachmentWrites)

  val migrationDataWrites: Writes[UploadMigrationDataRequest] = Writes(upload =>
    Json.obj(
      "id"       -> FilenameUtil.toCsvID(upload.fileName),
      "fileName" -> upload.fileName,
      "mimeType" -> upload.mimeType
    )
  )

  implicit val migrationDataFormat: Format[UploadMigrationDataRequest] =
    Format(Json.reads[UploadMigrationDataRequest], migrationDataWrites)

  val historicDataWrites: Writes[UploadHistoricDataRequest] = Writes(upload =>
    Json.obj(
      "id"       -> FilenameUtil.toCsvID(upload.fileName),
      "fileName" -> upload.fileName,
      "mimeType" -> upload.mimeType
    )
  )

  implicit val historicDataFormat: Format[UploadHistoricDataRequest] =
    Format(Json.reads[UploadHistoricDataRequest], historicDataWrites)

  implicit val format: Format[UploadRequest] = Union
    .from[UploadRequest]("type")
    .and[UploadAttachmentRequest](Attachment)
    .and[UploadMigrationDataRequest](MigrationData)
    .and[UploadHistoricDataRequest](HistoricData)
    .format
}
