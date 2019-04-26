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

import java.time.Instant

import org.mockito.BDDMockito._
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec

class CaseTest extends UnitSpec with MockitoSugar {

  "Case" should {
    val application = mock[Application]
    val applicationAnonymized = mock[Application]
    val operator = mock[Operator]
    val decision = mock[Decision]
    val attachment = mock[Attachment]

    "Anonymize" in {
      given(application.anonymize) willReturn applicationAnonymized

      Case(
        "ref",
        CaseStatus.OPEN,
        Instant.MIN,
        1,
        2,
        Some(Instant.MAX),
        Some("file no."),
        Some(operator),
        Some("queue"),
        application,
        Some(decision),
        Seq(attachment),
        Set("keyword")
      ).anonymize shouldBe Case(
        "ref",
        CaseStatus.OPEN,
        Instant.MIN,
        1,
        2,
        Some(Instant.MAX),
        Some("file no."),
        Some(operator),
        Some("queue"),
        applicationAnonymized,
        Some(decision),
        Seq(attachment),
        Set("keyword")
      )
    }
  }

}
