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
import uk.gov.hmrc.bindingtariffadminfrontend.model.MonitorStatistics
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.ApplicationType
import uk.gov.hmrc.bindingtariffadminfrontend.service.AdminMonitorService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class MonitorControllerControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val service = mock[AdminMonitorService]
  private val controller =
    new MonitorController(new SuccessfulAuthenticatedAction, service, mcc, messageApi, mockAppConfig)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(mockAppConfig.analyticsToken) willReturn "token"
    given(mockAppConfig.analyticsHost) willReturn "host"
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.reset(mockAppConfig)
  }

  private val statistics = MonitorStatistics(
    submittedCases = Map(
      ApplicationType.BTI             -> 2,
      ApplicationType.LIABILITY_ORDER -> 3,
      ApplicationType.CORRESPONDENCE  -> 4,
      ApplicationType.MISCELLANEOUS   -> 5
    ),
    migratedCases = Map(
      ApplicationType.BTI             -> 12,
      ApplicationType.LIABILITY_ORDER -> 13,
      ApplicationType.CORRESPONDENCE  -> 14,
      ApplicationType.MISCELLANEOUS   -> 15
    ),
    publishedFileCount      = 105,
    unpublishedFileCount    = 95,
    migratedAttachmentCount = 66
  )

  "GET /" should {
    "return 200" in {
      given(service.getStatistics(any[HeaderCarrier])).willReturn(Future.successful(statistics))

      val result: Result = await(controller.get()(FakeRequest()))

      status(result) shouldBe OK
      bodyOf(result) should include("monitor-heading")
    }
  }

}
