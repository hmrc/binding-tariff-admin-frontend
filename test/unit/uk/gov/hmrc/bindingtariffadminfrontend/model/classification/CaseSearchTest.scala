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

import java.net.URLDecoder
import java.time.Instant

import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.CaseStatus._
import uk.gov.hmrc.play.test.UnitSpec

class CaseSearchTest extends UnitSpec {

  private val search = CaseSearch(
    reference = Some(Set("id1", "id2")),
    applicationType = Some(ApplicationType.BTI),
    traderName = Some("trader-name"),
    queueId = Some("queue-id"),
    eori = Some("eori-number"),
    assigneeId = Some("assignee-id"),
    statuses = Some(Set(NEW, OPEN)),
    minDecisionEnd = Some(Instant.EPOCH),
    keywords = Some(Set("BIKE", "MTB")),
    decisionDetails = Some("decision-details")
  )

  private val params: Map[String, Seq[String]] = Map(
    "reference" -> Seq("id1", "id2"),
    "application_type" -> Seq("BTI"),
    "trader_name" -> Seq("trader-name"),
    "queue_id" -> Seq("queue-id"),
    "eori" -> Seq("eori-number"),
    "assignee_id" -> Seq("assignee-id"),
    "status" -> Seq("NEW", "OPEN"),
    "min_decision_end" -> Seq("1970-01-01T00:00:00Z"),
    "decision_details" -> Seq("decision-details"),
    "keyword" -> Seq("BIKE", "MTB")
  )

  private val emptyParams: Map[String, Seq[String]] = Map(
    "reference" -> Seq(""),
    "application_type" -> Seq(""),
    "trader_name" -> Seq(""),
    "queue_id" -> Seq(""),
    "eori" -> Seq(""),
    "assignee_id" -> Seq(""),
    "status" -> Seq(""),
    "min_decision_end" -> Seq(""),
    "keyword" -> Seq("")
  )

  "Case Search Binder" should {

    "Unbind Unpopulated Search to Query String" in {
      CaseSearch.bindable.unbind("", CaseSearch()) shouldBe ""
    }

    "Unbind Populated Search to Query String" in {
      val populatedQueryParam: String =
        "reference=id1" +
          "&reference=id2" +
          "&application_type=BTI" +
          "&queue_id=queue-id" +
          "&eori=eori-number" +
          "&assignee_id=assignee-id" +
          "&status=NEW" +
          "&status=OPEN" +
          "&trader_name=trader-name" +
          "&min_decision_end=1970-01-01T00:00:00Z" +
          "&decision_details=decision-details" +
          "&keyword=BIKE" +
          "&keyword=MTB"
      URLDecoder.decode(CaseSearch.bindable.unbind("", search), "UTF-8") shouldBe populatedQueryParam
    }

    "Bind empty query string" in {
      CaseSearch.bindable.bind("", Map()) shouldBe Some(Right(CaseSearch()))
    }

    "Bind query string with empty values" in {
      CaseSearch.bindable.bind("", emptyParams) shouldBe Some(Right(CaseSearch()))
    }

    "Bind populated query string" in {
      CaseSearch.bindable.bind("", params) shouldBe Some(Right(search))
    }
  }

  "Case Search Form" should {
    val map = params.mapValues(seq => seq.mkString(","))

    "Fill" in {
      CaseSearch.form.fill(search).data shouldBe map
    }

    "Bind from request" in {
      val form = CaseSearch.form.bind(map)
      form.hasErrors shouldBe false
      form.get shouldBe search
    }
  }

}
