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

case class UploadRequest
(
  fileName: String,
  mimeType: String
)

object UploadRequest {
  val writes: Writes[UploadRequest] = Writes(upload => Json.obj(
    "id" -> FilenameUtil.toID(upload.fileName),
    "fileName" -> upload.fileName,
    "mimeType" -> upload.mimeType
  ))
  implicit val format: Format[UploadRequest] = Format(Json.reads[UploadRequest], writes)
}
