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

package uk.gov.hmrc.bindingtariffadminfrontend.model

import java.time.Instant

import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.{Attachment, Operator}
import uk.gov.hmrc.play.test.UnitSpec

class MigratedAttachmentTest extends UnitSpec with MockitoSugar {

  "MigratedAttachment" should {
    val operator = mock[Operator]

    "Convert to attachment" in {
      MigratedAttachment(
        id = "id",
        filestoreId = "filestore-id",
        public = true,
        name = "name",
        mimeType = "type",
        user = Some(operator),
        timestamp = Instant.EPOCH
      ).asAttachment shouldBe Attachment(
        id = "filestore-id",
        public = true,
        timestamp = Instant.EPOCH
      )
    }
  }

}
