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
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import uk.gov.hmrc.bindingtariffadminfrontend.model.ScheduledJob
import uk.gov.hmrc.bindingtariffadminfrontend.service.AdminMonitorService
import uk.gov.hmrc.bindingtariffadminfrontend.views.html.scheduler
import uk.gov.hmrc.http.HeaderCarrier
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.Future

class SchedulerControllerControllerSpec extends ControllerSpec {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      bind[AdminMonitorService].toInstance(mock[AdminMonitorService]),
      bind[AuthenticatedAction].toInstance(new SuccessfulAuthenticatedAction)
    )
    .build()

  private val controller = inject[SchedulerController]

  override protected def afterEach(): Unit =
    super.afterEach()

  "GET /" should {
    "return 200" in {
      val request        = authenticatedRequest(FakeRequest("GET", "/"))
      val result: Result = await(controller.get()(request))
      status(result) shouldBe OK
//      contentAsString(result) shouldBe scheduler()(authenticatedRequest(request), messages, appConfig).toString()
    }
  }

  "POST /job" should {

    "return 200" in {

      val service = inject[AdminMonitorService]

      given(service.runScheduledJob(refEq(ScheduledJob.DAYS_ELAPSED))(any[HeaderCarrier])) willReturn Future.successful(
        (): Unit
      )

      val request        = authenticatedRequest(FakeRequest("POST", "/"))
      val result: Result = await(controller.post(ScheduledJob.DAYS_ELAPSED)(request))

      status(result) shouldBe OK
//      bodyOf(result) shouldBe scheduler(Some(ScheduledJob.DAYS_ELAPSED))(authenticatedRequest(request), messages, appConfig).toString()
    }
  }
}
