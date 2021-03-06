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

package uk.gov.hmrc.bindingtariffadminfrontend.controllers

import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status.OK
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.{Case, CaseSearch, Event, EventSearch}
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileSearch, FileUploaded}
import uk.gov.hmrc.bindingtariffadminfrontend.model.{Paged, Pagination}
import uk.gov.hmrc.bindingtariffadminfrontend.service.{AdminMonitorService, DataMigrationService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class SearchControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val migrationService = mock[DataMigrationService]
  private val service          = mock[AdminMonitorService]
  private val controller =
    new SearchController(new SuccessfulAuthenticatedAction, service, mcc, messageApi, mockAppConfig)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(mockAppConfig.analyticsToken) willReturn "token"
    given(mockAppConfig.analyticsHost) willReturn "host"
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.reset(migrationService, mockAppConfig)
  }

  "GET /" should {

    "return 200" in {
      given(service.getCases(any[CaseSearch], any[Pagination])(any[HeaderCarrier])) willReturn Future.successful(
        Paged.empty[Case]
      )
      given(service.getFiles(any[FileSearch], any[Pagination])(any[HeaderCarrier])) willReturn Future.successful(
        Paged.empty[FileUploaded]
      )
      given(service.getEvents(any[EventSearch], any[Pagination])(any[HeaderCarrier])) willReturn Future.successful(
        Paged.empty[Event]
      )

      val result: Result = await(controller.get(CaseSearch(), Pagination())(FakeRequest()))
      status(result) shouldBe OK
      bodyOf(result) should include("search-heading")
    }

    "return 200 with form errors" in {
      val result: Result =
        await(controller.get(CaseSearch(), Pagination())(FakeRequest().withFormUrlEncodedBody("status" -> "x")))
      status(result) shouldBe OK
      bodyOf(result) should include("search-heading")
    }
  }

}
