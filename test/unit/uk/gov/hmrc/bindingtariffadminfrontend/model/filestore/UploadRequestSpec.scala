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
import uk.gov.hmrc.bindingtariffadminfrontend.model.{AttachmentUpload, HistoricDataUpload, MigrationDataUpload}
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec

class UploadRequestSpec extends UnitSpec {
  private val attachmentUpload: UploadRequest = AttachmentUpload(
    fileName = "1234-01.jpg",
    mimeType = "image/jpg",
    id       = "id",
    batchId  = UUID.randomUUID().toString
  )

  private val migrationDataUpload: UploadRequest = MigrationDataUpload(
    fileName = "tblImages.csv",
    mimeType = "text/csv",
    id       = "id",
    batchId  = UUID.randomUUID().toString
  )

  private val historicDataUpload: UploadRequest = HistoricDataUpload(
    fileName = "ALLAPPLDATA-2004.txt",
    mimeType = "text/plain",
    id       = "id",
    batchId  = UUID.randomUUID().toString
  )

  "writes" should {
    "return valid json for attachments" in {
      Json.toJson(attachmentUpload)(UploadRequest.writes) shouldBe
        Json.parse("""{
               |        "id": "id",
               |        "fileName": "1234-01.jpg",
               |        "mimeType": "image/jpg",
               |        "publishable": true
               |      }""".stripMargin)
    }

    "return valid json for migration files" in {
      Json.toJson(migrationDataUpload)(UploadRequest.writes) shouldBe
        Json.parse("""{
                |       "id": "id",
                |       "fileName": "tblImages.csv",
                |       "mimeType": "text/csv",
                |       "publishable": false
                |     }""".stripMargin)
    }

    "return valid json for historic data files" in {
      Json.toJson(historicDataUpload)(UploadRequest.writes) shouldBe
        Json.parse("""{
                 |      "id": "id",
                 |      "fileName": "ALLAPPLDATA-2004.txt",
                 |      "mimeType": "text/plain",
                 |      "publishable": false
                 |    }""".stripMargin)
    }
  }
}
