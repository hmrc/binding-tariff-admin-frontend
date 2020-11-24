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

import java.util.UUID

import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffadminfrontend.model.AttachmentUpload
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec

class UploadRequestSpec extends UnitSpec {
  private val uploadRequest: UploadRequest = AttachmentUpload(
    fileName = "1234-01.jpg",
    mimeType = "image/jpg",
    id       = "id",
    batchId  = UUID.randomUUID().toString
  )

  "writes" should {
    "return valid json" in {
      Json.toJson(uploadRequest)(UploadRequest.writes) shouldBe
        Json.parse("""{
               |        "id": "id",
               |        "fileName": "1234-01.jpg",
               |        "mimeType": "image/jpg"
               |      }""".stripMargin)
    }
  }
}
