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

import java.time.temporal.ChronoUnit
import java.time.{Instant, ZonedDateTime}

import uk.gov.hmrc.bindingtariffadminfrontend.model.classification._

object Cases {

  val attachmentExample = Attachment(id = "id", public = true, timestamp = Instant.now())
  val eoriDetailsExample = EORIDetails("eori", "trader-business-name", "line1", "line2", "line3", "postcode", "country")
  val eoriAgentDetailsExample = AgentDetails(EORIDetails("eori", "agent-business-name", "line1", "line2", "line3", "postcode", "country"), Some(attachmentExample))
  val contactExample = Contact("name", "email", Some("phone"))
  val btiApplicationExample = BTIApplication(eoriDetailsExample, contactExample, Some(eoriAgentDetailsExample), false, "Laptop", "Personal Computer", None, None, None, None, None, None, false, false)
  val decision = Decision("AD12324FR", Instant.now(), Instant.now().plus(2*365, ChronoUnit.DAYS), "justification", "good description", None, None, Some("denomination"), None)
  val liabilityApplicationExample = LiabilityOrder(eoriDetailsExample, contactExample, LiabilityStatus.LIVE, "port", "entry number", ZonedDateTime.now())
  val btiCaseExample = Case("1", CaseStatus.OPEN, Instant.now(), 0, None, None, None, None, btiApplicationExample, Some(decision), Seq.empty, Set("k1", "k2"))

  val migratableAttachment = MigratableAttachment(public = true, url = "url", name = "name", mimeType = "text/plain", user = Some(Operator("id", Some("operator-name"))), timestamp = Instant.now())
  val migratableCase = MigratableCase("1", CaseStatus.OPEN, Instant.now(), 0, None, None, None, None, btiApplicationExample, Some(decision), Seq.empty, Set("k1", "k2"))

}
