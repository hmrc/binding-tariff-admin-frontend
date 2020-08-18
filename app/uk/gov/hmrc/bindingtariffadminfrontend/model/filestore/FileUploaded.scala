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

import java.time.LocalDate

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.ScanStatus.ScanStatus

case class FileUploaded
(
  id: String,
  fileName: String,
  mimeType: String,
  url: Option[String] = None,
  scanStatus: Option[ScanStatus] = None,
  published: Boolean = false
)

object FileUploaded {
  implicit val format: OFormat[FileUploaded] = Json.format[FileUploaded]
}

case class FileUploadSubmission (extractionDate: LocalDate, uploadedFiles: List[FileUploaded])

object FileUploadSubmission {
  implicit val format: OFormat[FileUploadSubmission] = Json.format[FileUploadSubmission]
}