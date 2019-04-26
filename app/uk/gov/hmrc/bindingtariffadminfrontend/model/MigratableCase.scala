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

import play.api.libs.json.{JsObject, Json, OFormat}
import play.json.extra.{InvariantFormat, Jsonx}
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification._

case class MigratableCase
(
  reference: String,
  status: CaseStatus,
  createdDate: Instant,
  daysElapsed: Long = 0,
  referredDaysElapsed: Long = 0,
  closedDate: Option[Instant] = None,
  caseBoardsFileNumber: Option[String] = None,
  assignee: Option[Operator] = None,
  queueId: Option[String] = None,
  application: Application,
  decision: Option[Decision] = None,
  attachments: Seq[MigratedAttachment],
  events: Seq[MigratableEvent],
  keywords: Set[String]
) {
  def toCase: Case = {
    Case(
      reference,
      status,
      createdDate,
      daysElapsed,
      referredDaysElapsed,
      closedDate,
      caseBoardsFileNumber,
      assignee,
      queueId,
      application,
      decision,
      attachments = attachments.map(m => m.asAttachment),
      keywords = keywords
    )
  }
}

object MigratableCase {
  object Mongo {
    private val fmt: InvariantFormat[MigratableCase] = Jsonx.formatCaseClass[MigratableCase]
    implicit val format: OFormat[MigratableCase] = OFormat(fmt.reads, fmt.writes(_).as[JsObject])
  }
  object REST {
    implicit val format: OFormat[MigratableCase] = Json.format[MigratableCase]
  }
}