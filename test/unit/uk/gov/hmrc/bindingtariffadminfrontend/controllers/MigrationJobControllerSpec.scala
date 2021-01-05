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
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtariffadminfrontend.model.{MigrationJob, ScheduledJob}
import uk.gov.hmrc.bindingtariffadminfrontend.service.AdminMonitorService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class MigrationJobControllerSpec extends ControllerSpec {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      bind[AdminMonitorService].toInstance(mock[AdminMonitorService]),
      bind[AuthenticatedAction].toInstance(new SuccessfulAuthenticatedAction)
    )
    .build()

  private val controller = inject[MigrationJobController]

  override protected def afterEach(): Unit =
    super.afterEach()

  "GET /" should {
    "return 200" in {
      val request        = authenticatedRequest(FakeRequest("GET", "/"))
      val result: Result = await(controller.get()(request))
      status(result) shouldBe OK
    }
  }

  "POST /job" should {

    "return 200" in {

      val service = inject[AdminMonitorService]

      given(service.runMigrationJob(refEq(MigrationJob.AMEND_DATE_OF_EXTRACT))(any[HeaderCarrier])) willReturn Future
        .successful(
          (): Unit
        )

      val request        = authenticatedRequest(FakeRequest("POST", "/"))
      val result: Result = await(controller.post(MigrationJob.AMEND_DATE_OF_EXTRACT)(request))

      status(result) shouldBe OK
    }
  }
}
