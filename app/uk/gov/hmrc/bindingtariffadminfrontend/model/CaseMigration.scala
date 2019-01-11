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

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.bindingtariffadminfrontend.model
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigrationStatus.MigrationStatus
import uk.gov.hmrc.bindingtariffadminfrontend.util.EnumJson

case class CaseMigration
(
  `case`: Case,
  status: MigrationStatus = MigrationStatus.UNPROCESSED,
  message: Option[String] = None
)

object CaseMigration {
  implicit val format: OFormat[CaseMigration] = Json.format[CaseMigration]
}

object MigrationStatus extends Enumeration {
  type MigrationStatus = Value
  val UNPROCESSED, SUCCESS, FAILED = Value

  def apply(string: String): Option[MigrationStatus] = {
    values.find(_.toString == string)
  }

  implicit val format: Format[model.MigrationStatus.Value] = EnumJson.format(MigrationStatus)
}
