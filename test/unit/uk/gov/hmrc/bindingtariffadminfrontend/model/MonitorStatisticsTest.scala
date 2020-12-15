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

import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.ApplicationType
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec

class MonitorStatisticsTest extends UnitSpec {

  private val statistics = MonitorStatistics(
    submittedCases          = Map(ApplicationType.BTI -> 2, ApplicationType.LIABILITY_ORDER -> 3),
    migratedCases           = Map(ApplicationType.BTI -> 12, ApplicationType.LIABILITY_ORDER -> 13),
    publishedFileCount      = 105,
    unpublishedFileCount    = 95,
    migratedAttachmentCount = 66
  )

  "allCases" should {
    "return the correct map" in {
      statistics.allCases shouldBe Map(ApplicationType.BTI -> 14, ApplicationType.LIABILITY_ORDER -> 16)
    }
  }

  "totalCaseCount" should {
    "return the correct number" in {
      statistics.totalCaseCount shouldBe 30
    }
  }

  "totalFileCount" should {
    "return the correct number" in {
      statistics.totalFileCount shouldBe 200
    }
  }
}
