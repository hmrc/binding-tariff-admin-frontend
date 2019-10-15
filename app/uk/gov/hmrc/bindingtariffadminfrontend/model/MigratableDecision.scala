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

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.{Appeal, Cancellation, Decision}

case class MigratableDecision
(
  bindingCommodityCode: String,
  effectiveStartDate: Option[Instant],
  effectiveEndDate: Option[Instant],
  justification: String,
  goodsDescription: String,
  methodSearch: Option[String] = None,
  methodCommercialDenomination: Option[String] = None,
  methodExclusion: Option[String] = None,
  appeal: Option[Seq[Appeal]] = None,
  cancellation: Option[Cancellation] = None
) {
  def toDecision: Decision = {
    Decision(
      bindingCommodityCode,
      effectiveStartDate,
      effectiveEndDate,
      justification,
      goodsDescription,
      methodSearch,
      methodCommercialDenomination,
      methodExclusion,
      appeal.getOrElse(Seq.empty),
      cancellation
    )
  }
}

object MigratableDecision {

  implicit val reads: Reads[MigratableDecision] = {
    ((JsPath \ "bindingCommodityCode").read[String] and
      (JsPath \ "effectiveStartDate").readNullable[Instant] and
      (JsPath \ "effectiveEndDate").readNullable[Instant] and
      (JsPath \ "justification").read[String] and
      (JsPath \ "goodsDescription").read[String] and
      (JsPath \ "methodSearch").readNullable[String] and
      (JsPath \ "methodCommercialDenomination").readNullable[String] and
      (JsPath \ "methodExclusion").readNullable[String] and
      ((JsPath \ "appeal").readNullable[Seq[Appeal]] or (JsPath \ "appeal").readNullable[Appeal].map(_.map(Seq(_)))) and
      (JsPath \ "cancellation").readNullable[Cancellation]) (MigratableDecision.apply _)
  }

  implicit val writes: Writes[MigratableDecision] = Json.writes[MigratableDecision]
}
