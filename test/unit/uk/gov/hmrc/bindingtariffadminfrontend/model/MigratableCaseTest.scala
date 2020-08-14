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

package uk.gov.hmrc.bindingtariffadminfrontend.model

import java.time.Instant

import org.mockito.BDDMockito.given
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.CaseStatus.SUPPRESSED
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.SampleStatus.DESTROYED
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification._
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec

class MigratableCaseTest extends UnitSpec with MockitoSugar {

  private val application = mock[Application]
  private val decision = mock[Decision]
  private val migratableDecision = mock[MigratableDecision]
  private val attachment = mock[Attachment]
  private val migratedAttachment = mock[MigratedAttachment]
  private val event = mock[MigratableEvent]

  "Migratable Case" should {

    given(migratedAttachment.asAttachment) willReturn attachment
    given(migratableDecision.toDecision) willReturn decision

    "Convert To Case" in {
      MigratableCase(
        reference = "ref",
        status = SUPPRESSED,
        createdDate = Instant.EPOCH,
        daysElapsed = 10,
        referredDaysElapsed = Some(20),
        closedDate = Some(Instant.EPOCH),
        caseBoardsFileNumber = Some("case-boards-number"),
        assignee = Some(Operator("operator")),
        queueId = Some("queue"),
        application = application,
        decision = Some(migratableDecision),
        attachments = Seq(migratedAttachment),
        events = Seq(event),
        keywords = Set("keyword1", "keyword2"),
        sampleStatus = Some(DESTROYED)
      ).toCase shouldBe Case(
        "ref",
        status = SUPPRESSED,
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
        keywords = Set("keyword1", "keyword2"),
        sample = Sample(status = Some(DESTROYED))
      )
    }

    "Convert To Case with defaults" in {
      MigratableCase(
        reference = "ref",
        status = SUPPRESSED,
        createdDate = Instant.EPOCH,
        daysElapsed = 10,
        application = application,
        attachments = Seq(migratedAttachment),
        events = Seq(event),
        keywords = Set("keyword1", "keyword2")
      ).toCase shouldBe Case(
        "ref",
        status = SUPPRESSED,
        createdDate = Instant.EPOCH,
        daysElapsed = 10,
        referredDaysElapsed = 0,  // Note: type conversion
        closedDate = None,
        caseBoardsFileNumber = None,
        assignee = None,
        queueId = None,
        application = application,
        decision = None,
        attachments = Seq(attachment),
        keywords = Set("keyword1", "keyword2"),
        sample = Sample()
      )
    }
  }

}
