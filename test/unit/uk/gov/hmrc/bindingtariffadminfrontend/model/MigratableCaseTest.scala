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

import org.mockito.BDDMockito.given
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification._
import uk.gov.hmrc.play.test.UnitSpec

class MigratableCaseTest extends UnitSpec with MockitoSugar {

  private val application = mock[Application]
  private val decision = mock[Decision]
  private val attachment = mock[Attachment]
  private val migratedAttachment = mock[MigratedAttachment]
  private val event = mock[MigratableEvent]

  "Migratable Case" should {

    given(migratedAttachment.asAttachment) willReturn attachment

    "Convert To Case" in {
      MigratableCase(
        reference = "ref",
        status = CaseStatus.SUPPRESSED,
        createdDate = Instant.EPOCH,
        daysElapsed = 10,
        referredDaysElapsed = 20,
        closedDate = Some(Instant.EPOCH),
        caseBoardsFileNumber = Some("case-boards-number"),
        assignee = Some(Operator("operator")),
        queueId = Some("queue"),
        application = application,
        decision = Some(decision),
        attachments = Seq(migratedAttachment),
        events = Seq(event),
        keywords = Set("keyword1", "keyword2")
      ).toCase shouldBe Case(
        "ref",
        status = CaseStatus.SUPPRESSED,
        createdDate = Instant.EPOCH,
        daysElapsed = 10,
        referredDaysElapsed = 20,
        closedDate = Some(Instant.EPOCH),
        caseBoardsFileNumber = Some("case-boards-number"),
        assignee = Some(Operator("operator")),
        queueId = Some("queue"),
        application = application,
        decision = Some(decision),
        attachments = Seq(attachment),
        keywords = Set("keyword1", "keyword2")
      )
    }
  }

}
