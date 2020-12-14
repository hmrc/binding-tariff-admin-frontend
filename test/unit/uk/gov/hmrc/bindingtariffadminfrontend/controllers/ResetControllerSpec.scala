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
import play.api.libs.json.Json
import play.api.mvc.Result
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.forms.{ResetFormProvider, ResetMigrationFormProvider}
import uk.gov.hmrc.bindingtariffadminfrontend.model.Store.Store
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.service.{DataMigrationService, ResetService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class ResetControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val resetService         = mock[ResetService]
  private val dataMigrationService = mock[DataMigrationService]
  private val appConfig            = mock[AppConfig]

  private val resetFormProvider          = new ResetFormProvider
  private val resetMigrationFormProvider = new ResetMigrationFormProvider

  private val controller = new ResetController(
    authenticatedAction        = new SuccessfulAuthenticatedAction,
    resetService               = resetService,
    dataMigrationService       = dataMigrationService,
    resetFormProvider          = resetFormProvider,
    resetMigrationFormProvider = resetMigrationFormProvider,
    mcc                        = mcc,
    messagesApi                = messageApi,
    appConfig                  = appConfig
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(appConfig.analyticsToken) willReturn "token"
    given(appConfig.analyticsHost) willReturn "host"
    given(appConfig.pageSize) willReturn 1
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.reset(resetService, dataMigrationService, appConfig)
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

  "GET /resetMigration" should {
    "return 200 when permitted and there are migrated cases" in {
      given(appConfig.resetMigrationPermitted) willReturn true
      given(dataMigrationService.counts) willReturn Future.successful(
        new MigrationCounts(
          Map(
            MigrationStatus.SUCCESS         -> 35,
            MigrationStatus.PARTIAL_SUCCESS -> 15,
            MigrationStatus.FAILED          -> 10,
            MigrationStatus.UNPROCESSED     -> 0
          )
        )
      )
      given(dataMigrationService.migratedCaseCount(any[HeaderCarrier])) willReturn Future.successful(50)

      val result: Result = await(controller.resetMigration()(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("Are You Sure?")
      bodyOf(result) should include("This will delete all 50 migrated cases and their associated data.")
    }

    "return 200 when permitted and there are no migrated cases" in {
      given(appConfig.resetMigrationPermitted) willReturn true
      given(dataMigrationService.counts) willReturn Future.successful(new MigrationCounts(Map()))
      given(dataMigrationService.migratedCaseCount(any[HeaderCarrier])) willReturn Future.successful(0)

      val result: Result = await(controller.resetMigration()(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("No migrated cases found")
    }

    "return 200 when permitted and there is a migration in process" in {
      given(appConfig.resetMigrationPermitted) willReturn true
      given(dataMigrationService.counts) willReturn Future.successful(
        new MigrationCounts(Map(MigrationStatus.UNPROCESSED -> 1))
      )
      given(dataMigrationService.migratedCaseCount(any[HeaderCarrier])) willReturn Future.successful(0)

      val result: Result = await(controller.resetMigration()(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("Migration in progress")
    }

    "return 303 when not permitted" in {
      given(appConfig.resetMigrationPermitted) willReturn false
      val result: Result = await(controller.resetMigration()(newFakeGETRequestWithCSRF))
      status(result)     shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin")
    }
  }

  "POST /resetMigration" should {
    "return 303 when permitted" in {
      given(appConfig.resetMigrationPermitted) willReturn true
      given(resetService.initiateResetMigratedCases()(any[HeaderCarrier])) willReturn Future.successful(true)
      given(dataMigrationService.totalCaseCount(any[HeaderCarrier])) willReturn Future.successful(100)
      given(dataMigrationService.migratedCaseCount(any[HeaderCarrier])) willReturn Future.successful(50)

      val result: Result =
        await(
          controller.resetMigrationConfirm()(newFakeGETRequestWithCSRF.withFormUrlEncodedBody("confirm" -> "true"))
        )
      status(result) shouldBe OK
      bodyOf(result) should include("Reset migrated cases")
      verify(resetService).initiateResetMigratedCases()(any[HeaderCarrier])
    }

    "return 303 when not permitted" in {
      given(appConfig.resetMigrationPermitted) willReturn false
      val result: Result = await(controller.resetMigrationConfirm()(newFakeGETRequestWithCSRF))

      status(result)     shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin")
      verify(resetService, never()).resetMigratedCases()(any[HeaderCarrier])
    }
  }

  "GET /migratedCaseInfo" should {
    "return 200" in {
      given(dataMigrationService.migratedCaseCount(any[HeaderCarrier]))
        .willReturn(
          Future.successful(2)
        )

      val result: Result = await(controller.migratedCaseInfo()(newFakeRequestWithCSRF))

      status(result)     shouldBe OK
      jsonBodyOf(result) shouldBe Json.obj("migratedCaseCount" -> 2)
    }
  }

  private def locationOf(result: Result): Option[String] =
    result.header.headers.get(LOCATION)

}
