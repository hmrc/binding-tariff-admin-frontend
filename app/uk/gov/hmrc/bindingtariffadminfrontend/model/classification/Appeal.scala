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

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads, Writes, _}
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.AppealStatus.AppealStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.AppealType.AppealType

case class Appeal
(
  id: String,
  status: AppealStatus,
  `type`: AppealType
)

object Appeal {
  //implicit val outboundFormat: OFormat[Appeal] = Json.format[Appeal]

  implicit val reads: Reads[Appeal] = {
    ((JsPath \ "id").readNullable[String].map(_.getOrElse("default id")) and
      (JsPath \ "status").readNullable[AppealStatus].map(_.getOrElse(AppealStatus.IN_PROGRESS)) and
      (JsPath \ "type").readNullable[AppealType].map(_.getOrElse(AppealType.REVIEW))) (Appeal.apply _)
  }

  implicit val writes: Writes[Appeal] = Json.writes[Appeal]
}
