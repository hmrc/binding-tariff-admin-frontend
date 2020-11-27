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
import org.mockito.Mockito.{never, verify}
import org.scalatest.BeforeAndAfterEach
import play.api.http.HeaderNames.LOCATION
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.Result
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.Store.Store
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.service.ResetService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class ResetControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val resetService = mock[ResetService]
  private val appConfig    = mock[AppConfig]

  private val controller = new ResetController(
    authenticatedAction = new SuccessfulAuthenticatedAction,
    service             = resetService,
    mcc                 = mcc,
    messagesApi         = messageApi,
    appConfig           = appConfig
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(appConfig.analyticsToken) willReturn "token"
    given(appConfig.analyticsHost) willReturn "host"
    given(appConfig.pageSize) willReturn 1
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.reset(resetService, appConfig)
  }

  "GET /reset" should {
    "return 200 when permitted" in {
      given(appConfig.resetPermitted) willReturn true
      val result: Result = await(controller.reset()(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("Are You Sure?")
    }

    "return 303 when not permitted" in {
      given(appConfig.resetPermitted) willReturn false
      val result: Result = await(controller.reset()(newFakeGETRequestWithCSRF))
      status(result)     shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin")
    }
  }

  "POST /reset" should {
    "return 303 when permitted" in {
      given(appConfig.resetPermitted) willReturn true
      given(resetService.resetEnvironment(any[Set[Store]])(any[HeaderCarrier])) willReturn Future.successful(
        (): Unit
      )

      val result: Result =
        await(controller.resetConfirm()(newFakeGETRequestWithCSRF.withFormUrlEncodedBody("store[0]" -> "CASES")))
      status(result)     shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
      verify(resetService).resetEnvironment(refEq(Set(Store.CASES)))(any[HeaderCarrier])
    }

    "return 303 when not permitted" in {
      given(appConfig.resetPermitted) willReturn false
      val result: Result = await(controller.resetConfirm()(newFakeGETRequestWithCSRF))

      status(result)     shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
      verify(resetService, never()).resetEnvironment(any[Set[Store]])(any[HeaderCarrier])
    }
  }

  private def locationOf(result: Result): Option[String] =
    result.header.headers.get(LOCATION)

}
