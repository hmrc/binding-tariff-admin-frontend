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
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification._
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec

class MigratableDecisionTest extends UnitSpec with MockitoSugar {

  "Migratable Decision" should {

    "Convert To Decision" in {
      MigratableDecision(
        bindingCommodityCode         = "bindingCommodityCode",
        effectiveStartDate           = Some(Instant.EPOCH),
        effectiveEndDate             = Some(Instant.EPOCH),
        justification                = "justification",
        goodsDescription             = "goodsDescription",
        methodSearch                 = Some("methodSearch"),
        methodCommercialDenomination = Some("methodCommercialDenomination"),
        methodExclusion              = Some("methodExclusion"),
        appeal                       = Some(Seq(Appeal("1", AppealStatus.DISMISSED, AppealType.REVIEW))),
        cancellation                 = Some(Cancellation(CancelReason.ANNULLED, true))
      ).toDecision shouldBe Decision(
        bindingCommodityCode         = "bindingCommodityCode",
        effectiveStartDate           = Some(Instant.EPOCH),
        effectiveEndDate             = Some(Instant.EPOCH),
        justification                = "justification",
        goodsDescription             = "goodsDescription",
        methodSearch                 = Some("methodSearch"),
        methodCommercialDenomination = Some("methodCommercialDenomination"),
        methodExclusion              = Some("methodExclusion"),
        appeal                       = Seq(Appeal("1", AppealStatus.DISMISSED, AppealType.REVIEW)),
        cancellation                 = Some(Cancellation(CancelReason.ANNULLED, true))
      )
    }

    "Convert To Decision with defaults" in {
      MigratableDecision(
        bindingCommodityCode = "bindingCommodityCode",
        effectiveStartDate   = Some(Instant.EPOCH),
        effectiveEndDate     = Some(Instant.EPOCH),
        justification        = "justification",
        goodsDescription     = "goodsDescription"
      ).toDecision shouldBe Decision(
        bindingCommodityCode         = "bindingCommodityCode",
        effectiveStartDate           = Some(Instant.EPOCH),
        effectiveEndDate             = Some(Instant.EPOCH),
        justification                = "justification",
        goodsDescription             = "goodsDescription",
        methodSearch                 = None,
        methodCommercialDenomination = None,
        methodExclusion              = None,
        appeal                       = Seq.empty, // Note: type conversion
        cancellation                 = None
      )
    }
  }

}
