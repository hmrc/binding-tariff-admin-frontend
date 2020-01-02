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

package uk.gov.hmrc.bindingtariffadminfrontend.controllers

import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import play.api.http.Status.OK
import play.api.mvc.Result
import uk.gov.hmrc.bindingtariffadminfrontend.model.ScheduledJob
import uk.gov.hmrc.bindingtariffadminfrontend.service.AdminMonitorService
import uk.gov.hmrc.bindingtariffadminfrontend.views.html.scheduler
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class SchedulerControllerControllerSpec extends ControllerSpec {

  private val service = mock[AdminMonitorService]
  private val controller = new SchedulerController(new SuccessfulAuthenticatedAction, service, messageApi, appConfig)

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.reset(appConfig)
  }

  "GET /" should {
    "return 200" in {
      val request = newFakeGETRequestWithCSRF
      val result: Result = await(controller.get()(request))
      status(result) shouldBe OK
      bodyOf(result) shouldBe scheduler()(authenticatedRequest(request), messages, appConfig).toString()
    }
  }

  "POST /job" should {
    "return 200" in {
      given(service.runScheduledJob(refEq(ScheduledJob.DAYS_ELAPSED))(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      val request = newFakePOSTRequestWithCSRF
      val result: Result = await(controller.post(ScheduledJob.DAYS_ELAPSED)(request))

      status(result) shouldBe OK
      bodyOf(result) shouldBe scheduler(Some(ScheduledJob.DAYS_ELAPSED))(authenticatedRequest(request), messages, appConfig).toString()
    }
  }
}