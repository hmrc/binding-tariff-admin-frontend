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
import uk.gov.hmrc.play.json.Union

sealed trait UploadRequest {
  def id: String
  def batchId: String
  def fileName: String
  def mimeType: String
}

case class UploadAttachmentRequest(
  override val fileName: String,
  override val mimeType: String,
  override val id: String,
  override val batchId: String
) extends UploadRequest

case class UploadMigrationDataRequest(
  override val fileName: String,
  override val mimeType: String,
  override val id: String,
  override val batchId: String
) extends UploadRequest

case class UploadHistoricDataRequest(
  override val fileName: String,
  override val mimeType: String,
  override val id: String,
  override val batchId: String
) extends UploadRequest

object UploadRequest {
  val Attachment    = classOf[UploadAttachmentRequest].getSimpleName()
  val MigrationData = classOf[UploadMigrationDataRequest].getSimpleName()
  val HistoricData  = classOf[UploadHistoricDataRequest].getSimpleName()

  implicit val attachmentFormat: Format[UploadAttachmentRequest]   = Json.format[UploadAttachmentRequest]
  implicit val migrationFormat: Format[UploadMigrationDataRequest] = Json.format[UploadMigrationDataRequest]
  implicit val historicFormat: Format[UploadHistoricDataRequest]   = Json.format[UploadHistoricDataRequest]

  implicit val format: Format[UploadRequest] = Union
    .from[UploadRequest]("type")
    .and[UploadAttachmentRequest](Attachment)
    .and[UploadMigrationDataRequest](MigrationData)
    .and[UploadHistoricDataRequest](HistoricData)
    .format
}
