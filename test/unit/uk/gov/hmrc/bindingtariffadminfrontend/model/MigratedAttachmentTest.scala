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

package uk.gov.hmrc.bindingtariffadminfrontend.model

import java.time.Instant

import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.{Attachment, Operator}
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec

class MigratedAttachmentTest extends UnitSpec with MockitoSugar {

  "MigratedAttachment" should {
    val operator = mock[Operator]

    "Generate ID" in {
      MigratedAttachment(
        public = true,
        name = "file name.jpg",
        operator = Some(operator),
        timestamp = Instant.EPOCH
      ).id shouldBe "file_name_jpg"
    }

    "Convert to attachment" in {
      MigratedAttachment(
        public = true,
        name = "file name",
        operator = Some(operator),
        timestamp = Instant.EPOCH
      ).asAttachment shouldBe Attachment(
        id = "file_name",
        public = true,
        operator = Some(operator),
        timestamp = Instant.EPOCH
      )
    }
  }

}
