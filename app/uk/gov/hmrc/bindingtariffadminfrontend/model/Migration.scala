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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.bindingtariffadminfrontend.model.CaseUpdateTarget.CaseUpdateTarget
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigrationStatus.MigrationStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.CaseUpdate

case class Migration(
  `case`: MigratableCase,
  status: MigrationStatus                    = MigrationStatus.UNPROCESSED,
  message: Seq[String]                       = Seq.empty,
  caseUpdate: Option[CaseUpdate]             = None,
  caseUpdateTarget: Option[CaseUpdateTarget] = None
) {
  def appendMessage(message: String): Migration =
    this.copy(message = this.message :+ message)

  def appendMessage(message: Seq[String]): Migration =
    this.copy(message = this.message ++ message)

  def isCaseUpdate: Boolean = caseUpdate.isDefined && caseUpdateTarget.isDefined
}

object Migration {
  object Mongo {
    private implicit val fmt: OFormat[MigratableCase]          = MigratableCase.Mongo.format
    private implicit val caseUpdateFormat: OFormat[CaseUpdate] = CaseUpdate.Mongo.formatCaseUpdate
    implicit val format: OFormat[Migration]                    = Json.format[Migration]
  }

  object REST {
    private implicit val fmt: OFormat[MigratableCase]          = MigratableCase.REST.format
    private implicit val caseUpdateFormat: OFormat[CaseUpdate] = CaseUpdate.REST.formatCaseUpdate
    implicit val format: OFormat[Migration]                    = Json.format[Migration]
  }
}
