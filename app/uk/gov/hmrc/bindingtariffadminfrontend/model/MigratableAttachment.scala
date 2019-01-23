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
import java.util.UUID

import play.api.libs.json.Format
import play.json.extra.Jsonx
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.Operator

case class MigratableAttachment
(
  id: String = UUID.randomUUID().toString,
  public: Boolean = false,
  url: String,
  name: String,
  mimeType: String,
  user: Option[Operator] = None,
  timestamp: Instant
)

object MigratableAttachment {
  implicit val format: Format[MigratableAttachment] = Jsonx.formatCaseClass[MigratableAttachment]
}