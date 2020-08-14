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

package uk.gov.hmrc.bindingtariffadminfrontend.model.classification

import uk.gov.hmrc.bindingtariffadminfrontend.model.Anonymize
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec

class EORIDetailsTest extends UnitSpec {

  "EORI Details" should {
    "Anonymize" in {
      EORIDetails(
        "eori",
        "business",
        "address 1",
        "address 2",
        "address 3",
        "postcode",
        "country"
      ).anonymize shouldBe EORIDetails(
        "eori",
        "business",
        Anonymize.anonymized,
        Anonymize.anonymized,
        Anonymize.anonymized,
        Anonymize.anonymized,
        Anonymize.anonymized
      )
    }
  }

}
