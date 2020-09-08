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

import java.time.Instant

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.AppealStatus.AppealStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.AppealType.AppealType
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.CancelReason.CancelReason
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.EventType.EventType
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.ReferralReason.ReferralReason
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.SampleReturn.SampleReturn
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.SampleStatus.SampleStatus
import uk.gov.hmrc.bindingtariffadminfrontend.util.JsonUtil
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
    .and[ReferralCaseStatusChange](EventType.CASE_REFERRAL.toString)
    .and[CompletedCaseStatusChange](EventType.CASE_COMPLETED.toString)
    .and[AppealStatusChange](EventType.APPEAL_STATUS_CHANGE.toString)
    .and[ExtendedUseStatusChange](EventType.EXTENDED_USE_STATUS_CHANGE.toString)
    .and[AssignmentChange](EventType.ASSIGNMENT_CHANGE.toString)
    .and[Note](EventType.NOTE.toString)
    .and[CaseCreated](EventType.CASE_CREATED.toString)
    .format
}

sealed trait OptionalComment {
  val comment: Option[String]
}

sealed trait OptionalAttachment {
  val attachmentId: Option[String]
}

sealed trait FieldChange[T] extends Details with OptionalComment {
  val from: T
  val to: T
  val comment: Option[String]
}

case class CaseStatusChange
(
  override val from: CaseStatus,
  override val to: CaseStatus,
  override val comment: Option[String] = None,
  override val attachmentId: Option[String] = None
) extends FieldChange[CaseStatus] with OptionalAttachment {
  override val `type`: EventType.Value = EventType.CASE_STATUS_CHANGE
}

object CaseStatusChange {
  implicit val format: OFormat[CaseStatusChange] = Json.format[CaseStatusChange]
}

case class CancellationCaseStatusChange
(
  override val from: CaseStatus,
  override val comment: Option[String] = None,
  override val attachmentId: Option[String] = None,
  reason: CancelReason
) extends FieldChange[CaseStatus] with OptionalAttachment {
  override val to: CaseStatus = CaseStatus.CANCELLED
  override val `type`: EventType.Value = EventType.CASE_CANCELLATION
}

object CancellationCaseStatusChange {
  implicit val format: OFormat[CancellationCaseStatusChange] = Json.format[CancellationCaseStatusChange]
}

case class ReferralCaseStatusChange
(
  override val from: CaseStatus,
  override val comment: Option[String] = None,
  override val attachmentId: Option[String] = None,
  referredTo: String,
  reason: Seq[ReferralReason]
) extends FieldChange[CaseStatus] with OptionalAttachment {
  override val to: CaseStatus = CaseStatus.REFERRED
  override val `type`: EventType.Value = EventType.CASE_REFERRAL
}

object ReferralCaseStatusChange {
  implicit val format: OFormat[ReferralCaseStatusChange] = Json.format[ReferralCaseStatusChange]
}

case class CompletedCaseStatusChange
(
  override val from: CaseStatus,
  override val comment: Option[String] = None,
  email: Option[String]
) extends FieldChange[CaseStatus] {
  override val to: CaseStatus = CaseStatus.COMPLETED
  override val `type`: EventType.Value = EventType.CASE_COMPLETED
}

object CompletedCaseStatusChange {
  implicit val format: OFormat[CompletedCaseStatusChange] = Json.format[CompletedCaseStatusChange]
}

case class CaseCreated
(
  comment: String
) extends Details {
  override val `type`: EventType = EventType.CASE_CREATED
}

object CaseCreated {
  implicit val format: OFormat[CaseCreated] = Json.format[CaseCreated]
}

case class AppealAdded
(
  appealType: AppealType,
  appealStatus: AppealStatus,
  override val comment: Option[String] = None
) extends Details with OptionalComment {
  override val `type`: EventType.Value = EventType.APPEAL_ADDED
}

object AppealAdded {
  implicit val format: OFormat[AppealAdded] = Json.format[AppealAdded]
}

case class AppealStatusChange
(
  appealType: AppealType,
  override val from: AppealStatus,
  override val to: AppealStatus,
  override val comment: Option[String] = None
) extends FieldChange[AppealStatus] {
  override val `type`: EventType.Value = EventType.APPEAL_STATUS_CHANGE
}

object AppealStatusChange {
  implicit val format: OFormat[AppealStatusChange] = Json.format[AppealStatusChange]
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

case class QueueChange
(
  override val from: Option[String],
  override val to: Option[String],
  override val comment: Option[String] = None
) extends FieldChange[Option[String]] {
  override val `type`: EventType.Value = EventType.QUEUE_CHANGE
}

object QueueChange {
  implicit val format: OFormat[QueueChange] = Json.format[QueueChange]
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

case class SampleStatusChange
(
  override val from: Option[SampleStatus],
  override val to: Option[SampleStatus],
  override val comment: Option[String] = None
) extends FieldChange[Option[SampleStatus]] {
  override val `type`: EventType.Value = EventType.SAMPLE_STATUS_CHANGE
}

object SampleStatusChange {
  implicit val format: OFormat[SampleStatusChange] = Json.format[SampleStatusChange]
}

case class SampleReturnChange
(
  override val from: Option[SampleReturn],
  override val to: Option[SampleReturn],
  override val comment: Option[String] = None
) extends FieldChange[Option[SampleReturn]] {
  override val `type`: EventType.Value = EventType.SAMPLE_RETURN_CHANGE
}

object SampleReturnChange {
  implicit val format: OFormat[SampleReturnChange] = Json.format[SampleReturnChange]
}

object EventType extends Enumeration {
  type EventType = Value
  val CASE_STATUS_CHANGE = Value
  val CASE_REFERRAL = Value
  val CASE_COMPLETED = Value
  val CASE_CANCELLATION = Value
  val APPEAL_STATUS_CHANGE = Value
  val APPEAL_ADDED = Value
  val EXTENDED_USE_STATUS_CHANGE = Value
  val ASSIGNMENT_CHANGE = Value
  val QUEUE_CHANGE = Value
  val NOTE = Value
  val SAMPLE_STATUS_CHANGE = Value
  val SAMPLE_RETURN_CHANGE = Value
  val CASE_CREATED = Value
  implicit val format: Format[classification.EventType.Value] = JsonUtil.format(EventType)
}
