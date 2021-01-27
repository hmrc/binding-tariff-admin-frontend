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

import uk.gov.hmrc.bindingtariffadminfrontend.model.CaseUpdateTarget.CaseUpdateTarget
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification._

object CaseUpdates {
  def createMigration(c: MigratableCase, updateTarget: CaseUpdateTarget): Option[Migration] = {
    val caseUpdate = updateTarget match {
      case CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME
          if c.application.`type` == ApplicationType.LIABILITY_ORDER &&
            c.application.asInstanceOf[LiabilityOrder].traderName.trim.nonEmpty =>
        Some(
          CaseUpdate(application =
            Some(
              LiabilityUpdate(traderName = SetValue(c.application.asInstanceOf[LiabilityOrder].traderName))
            )
          )
        )
      case _ => None
    }

    caseUpdate.map(update =>
      Migration(
        c,
        caseUpdate       = Some(update),
        caseUpdateTarget = Some(updateTarget)
      )
    )
  }

  def isUpdateRequired(c: Case, updateTarget: CaseUpdateTarget): Boolean =
    updateTarget match {
      case CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME =>
        c.application.`type` == ApplicationType.LIABILITY_ORDER &&
          c.application.asInstanceOf[LiabilityOrder].traderName.trim.isEmpty
      case _ => false
    }
}
