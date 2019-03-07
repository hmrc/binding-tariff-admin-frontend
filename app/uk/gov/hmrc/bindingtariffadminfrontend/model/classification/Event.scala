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

package uk.gov.hmrc.bindingtariffadminfrontend.model.classification

import java.time.Instant

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.AppealStatus.AppealStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.EventType.EventType
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.ReviewStatus.ReviewStatus
import uk.gov.hmrc.bindingtariffadminfrontend.util.EnumJson
import uk.gov.hmrc.play.json.Union



case class Event
(
  details: Details,
  operator: Operator,
  caseReference: String,
  timestamp: Instant
)

object Event {
  implicit val format: OFormat[Event] = Json.format[Event]
}

sealed trait Details {
  val `type`: EventType
}

object Details {
  implicit val format: Format[Details] = Union.from[Details]("type")
    .and[CaseStatusChange](EventType.CASE_STATUS_CHANGE.toString)
    .and[AppealStatusChange](EventType.APPEAL_STATUS_CHANGE.toString)
    .and[ReviewStatusChange](EventType.REVIEW_STATUS_CHANGE.toString)
    .and[ExtendedUseStatusChange](EventType.EXTENDED_USE_STATUS_CHANGE.toString)
    .and[AssignmentChange](EventType.ASSIGNMENT_CHANGE.toString)
    .and[Note](EventType.NOTE.toString)
    .format
}

sealed trait FieldChange[T] extends Details {
  val from: T
  val to: T
  val comment: Option[String]
}

case class CaseStatusChange
(
  override val from: CaseStatus,
  override val to: CaseStatus,
  override val comment: Option[String] = None
) extends FieldChange[CaseStatus] {
  override val `type`: EventType.Value = EventType.CASE_STATUS_CHANGE
}

object CaseStatusChange {
  implicit val format: OFormat[CaseStatusChange] = Json.format[CaseStatusChange]
}

case class AppealStatusChange
(
  override val from: Option[AppealStatus],
  override val to: Option[AppealStatus],
  override val comment: Option[String] = None
) extends FieldChange[Option[AppealStatus]] {
  override val `type`: EventType.Value = EventType.APPEAL_STATUS_CHANGE
}

object AppealStatusChange {
  implicit val format: OFormat[AppealStatusChange] = Json.format[AppealStatusChange]
}

case class ReviewStatusChange
(
  override val from: Option[ReviewStatus],
  override val to: Option[ReviewStatus],
  override val comment: Option[String] = None
) extends FieldChange[Option[ReviewStatus]] {
  override val `type`: EventType.Value = EventType.REVIEW_STATUS_CHANGE
}

object ReviewStatusChange {
  implicit val format: OFormat[ReviewStatusChange] = Json.format[ReviewStatusChange]
}

case class ExtendedUseStatusChange
(
  override val from: Boolean,
  override val to: Boolean,
  override val comment: Option[String] = None
) extends FieldChange[Boolean] {
  override val `type`: EventType.Value = EventType.EXTENDED_USE_STATUS_CHANGE
}

object ExtendedUseStatusChange {
  implicit val format: OFormat[ExtendedUseStatusChange] = Json.format[ExtendedUseStatusChange]
}

case class AssignmentChange
(
  override val from: Option[Operator],
  override val to: Option[Operator],
  override val comment: Option[String] = None
) extends FieldChange[Option[Operator]] {
  override val `type`: EventType.Value = EventType.ASSIGNMENT_CHANGE
}

object AssignmentChange {
  implicit val format: OFormat[AssignmentChange] = Json.format[AssignmentChange]
}

case class Note
(
  comment: String
) extends Details {
  override val `type`: EventType.Value = EventType.NOTE
}

object Note {
  implicit val format: OFormat[Note] = Json.format[Note]
}

object EventType extends Enumeration {
  type EventType = Value
  val CASE_STATUS_CHANGE, APPEAL_STATUS_CHANGE, REVIEW_STATUS_CHANGE, EXTENDED_USE_STATUS_CHANGE, ASSIGNMENT_CHANGE, NOTE = Value
  implicit val format: Format[classification.EventType.Value] = EnumJson.format(EventType)
}
