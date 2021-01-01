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

import uk.gov.hmrc.bindingtariffadminfrontend.model.MigrationStatus._
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec

class MigrationCountsTest extends UnitSpec {

  "MigrationCounts" should {
    val counts = new MigrationCounts(
      Map(
        SUCCESS     -> 1,
        FAILED      -> 2,
        UNPROCESSED -> 3
      )
    )

    "retrieve count" in {
      counts.get(SUCCESS)     shouldBe 1
      counts.get(FAILED)      shouldBe 2
      counts.get(UNPROCESSED) shouldBe 3
      counts.get(null)        shouldBe 0
    }

    "retrieve total" in {
      counts.total shouldBe 6
    }

    "retrieve processed" in {
      new MigrationCounts(Map(SUCCESS     -> 1, FAILED -> 1)).processed shouldBe 2
      new MigrationCounts(Map(UNPROCESSED -> 1)).processed shouldBe 0

    }

    "has Unprocessed" in {
      new MigrationCounts(Map(UNPROCESSED -> 1)).hasUnprocessed shouldBe true
      new MigrationCounts(Map()).hasUnprocessed shouldBe false
    }

    "is Empty" in {
      new MigrationCounts(Map(UNPROCESSED -> 1)).isEmpty shouldBe false
      new MigrationCounts(Map()).isEmpty shouldBe true
    }
  }
}
