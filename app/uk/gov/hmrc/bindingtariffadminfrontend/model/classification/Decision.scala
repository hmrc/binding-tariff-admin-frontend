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

import play.api.libs.json.{Json, OFormat}

case class Decision(
  bindingCommodityCode: String,
  effectiveStartDate: Option[Instant],
  effectiveEndDate: Option[Instant],
  justification: String,
  goodsDescription: String,
  methodSearch: Option[String]                 = None,
  methodCommercialDenomination: Option[String] = None,
  methodExclusion: Option[String]              = None,
  appeal: Seq[Appeal]                          = Seq.empty,
  cancellation: Option[Cancellation]           = None,
  explanation: Option[String]                  = None
)

object Decision {
  implicit val outboundFormat: OFormat[Decision] = Json.format[Decision]
}
