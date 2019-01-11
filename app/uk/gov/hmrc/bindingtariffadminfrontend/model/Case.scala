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

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.bindingtariffadminfrontend.model
import uk.gov.hmrc.bindingtariffadminfrontend.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffadminfrontend.util.EnumJson

case class Case
(
  reference: String,
  status: CaseStatus,
  createdDate: Instant,
  daysElapsed: Long,
  closedDate: Option[Instant] = None,
  caseBoardsFileNumber: Option[String] = None,
  assigneeId: Option[String] = None,
  queueId: Option[String] = None,
  application: Application,
  decision: Option[Decision] = None,
  attachments: Seq[Attachment] = Seq.empty
)

object Case {
  implicit val format: OFormat[Case] = Json.format[Case]
}

object CaseStatus extends Enumeration {
  type CaseStatus = Value
  val DRAFT, NEW, OPEN, SUPPRESSED, REFERRED, REJECTED, CANCELLED, SUSPENDED, COMPLETED, REVOKED, ANNULLED = Value
  implicit val format: Format[model.CaseStatus.Value] = EnumJson.format(CaseStatus)
}
