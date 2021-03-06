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

package uk.gov.hmrc.bindingtariffadminfrontend.model.classification

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.bindingtariffadminfrontend.model.Anonymize._

case class EORIDetails(
  eori: String,
  businessName: String,
  addressLine1: String,
  addressLine2: String,
  addressLine3: String,
  postcode: String,
  country: String
) {
  def anonymize: EORIDetails = this.copy(
    addressLine1 = anonymized,
    addressLine2 = anonymized,
    addressLine3 = anonymized,
    postcode     = anonymized,
    country      = anonymized
  )
}

object EORIDetails {
  implicit val format: OFormat[EORIDetails] = Json.format[EORIDetails]
}
