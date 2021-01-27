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

import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.{CaseUpdate, LiabilityUpdate, SetValue}
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec

class CaseUpdatesSpec extends UnitSpec {
  "createMigration" when {
    "called with LIAIBLITIES_APPLICATION_TRADERNAME" should {
      "return None for BTI's" in {
        CaseUpdates.createMigration(
          Cases.migratableCase.copy(application = Cases.btiApplicationExample),
          CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME
        ) shouldBe None
      }

      "return None when traderName is empty" in {
        val migratableCase =
          Cases.migratableCase.copy(application = Cases.liabilityApplicationExample.copy(traderName = ""))
        CaseUpdates.createMigration(
          migratableCase,
          CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME
        ) shouldBe None
      }

      "return a valid migration for liabilites when traderName is non empty" in {
        val migratableCase =
          Cases.migratableCase.copy(application = Cases.liabilityApplicationExample.copy(traderName = "trader name"))
        CaseUpdates.createMigration(migratableCase, CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME) shouldBe Some(
          Migration(
            `case`           = migratableCase,
            status           = MigrationStatus.UNPROCESSED,
            message          = Seq.empty,
            caseUpdate       = Some(CaseUpdate(Some(LiabilityUpdate(traderName = SetValue("trader name"))))),
            caseUpdateTarget = Some(CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME)
          )
        )
      }
    }
  }

  "isUpdateRequired" when {
    "called with LIAIBLITIES_APPLICATION_TRADERNAME" should {
      "return false for BTI's" in {
        CaseUpdates
          .isUpdateRequired(Cases.btiCaseExample, CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME) shouldBe false
      }

      "return false when traderName is populated" in {
        CaseUpdates.isUpdateRequired(
          Cases.liabilityCaseExample
            .copy(application = Cases.liabilityApplicationExample.copy(traderName = "trader name")),
          CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME
        ) shouldBe false
      }

      "return true when traderName is empty" in {
        CaseUpdates.isUpdateRequired(
          Cases.liabilityCaseExample.copy(application = Cases.liabilityApplicationExample.copy(traderName = "")),
          CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME
        ) shouldBe true
      }
    }
  }
}
