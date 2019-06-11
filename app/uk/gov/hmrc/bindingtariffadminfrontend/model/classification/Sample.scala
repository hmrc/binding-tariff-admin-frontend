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

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.SampleStatus.SampleStatus
import uk.gov.hmrc.bindingtariffadminfrontend.util.JsonUtil

case class Sample
(
  status: Option[SampleStatus] = None,
  requestedBy: Option[Operator] = None,
  returnStatus: Option[String] = None
)

object Sample {
  implicit val format: OFormat[Sample] = Json.format[Sample]
}

object SampleStatus extends Enumeration {
  type SampleStatus = Value
  val AWAITING, MOVED_TO_ACT, MOVED_TO_ELM, SENT_FOR_ANALYSIS, SENT_TO_APPEALS, STORAGE, RETURNED_APPLICANT,
  RETURNED_PORT_OFFICER, RETURNED_COURIER, DESTROYED = Value
  implicit val format: Format[SampleStatus.Value] = JsonUtil.format(SampleStatus)
}

