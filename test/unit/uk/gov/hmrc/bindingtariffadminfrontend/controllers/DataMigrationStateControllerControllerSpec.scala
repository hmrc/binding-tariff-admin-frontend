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

package uk.gov.hmrc.bindingtariffadminfrontend.controllers

import akka.stream.Materializer
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.{never, verify}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import play.api.http.HeaderNames.LOCATION
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.i18n.{DefaultLangs, DefaultMessagesApi}
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.{Configuration, Environment}
import play.filters.csrf.CSRF.{Token, TokenProvider}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.{MigrationCounts, MigrationStatus}
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class DataMigrationStateControllerControllerSpec extends WordSpec
  with Matchers
  with UnitSpec
  with MockitoSugar
  with WithFakeApplication
  with BeforeAndAfterEach {

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)
  private val migrationService = mock[DataMigrationService]
  private val messageApi = new DefaultMessagesApi(env, configuration, new DefaultLangs(configuration))
  private val appConfig = mock[AppConfig]
  private implicit val mat: Materializer = fakeApplication.materializer
  private val controller = new DataMigrationStateController(new SuccessfulAuthenticatedAction, migrationService, messageApi, appConfig)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(appConfig.analyticsToken) willReturn "token"
    given(appConfig.analyticsHost) willReturn "host"
    given(appConfig.pageSize) willReturn 1
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.reset(migrationService, appConfig)
  }

  "GET /state" should {
    "return 200 when not in progress" in {
      given(migrationService.counts) willReturn Future.successful(new MigrationCounts(Map()))
      given(migrationService.getState(0, 1, Seq(MigrationStatus.SUCCESS))) willReturn Future.successful(Seq.empty)
      val result: Result = await(controller.get(0, Seq(MigrationStatus.SUCCESS.toString))(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("data_migration_state-complete-heading")
    }

    "return 200 when in progress" in {
      given(migrationService.counts) willReturn Future.successful(new MigrationCounts(Map(MigrationStatus.UNPROCESSED -> 1)))
      given(migrationService.getState(0, 1, Seq(MigrationStatus.SUCCESS))) willReturn Future.successful(Seq.empty)
      val result: Result = await(controller.get(0, Seq(MigrationStatus.SUCCESS.toString))(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("data_migration_state-in_progress-heading")
    }

    "paginates" in {
      given(migrationService.counts) willReturn Future.successful(new MigrationCounts(Map()))
      given(migrationService.getState(0, 1, Seq(MigrationStatus.SUCCESS))) willReturn Future.successful(Seq.empty)
      val result: Result = await(controller.get(0, Seq(MigrationStatus.SUCCESS.toString))(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
    }
  }

  "DELETE /state" should {
    "return 303" in {
      given(migrationService.clear(None)) willReturn Future.successful(true)
      val result: Result = await(controller.delete(None)(newFakeGETRequestWithCSRF))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }

    "return 303 with query params" in {
      given(migrationService.clear(Some(MigrationStatus.UNPROCESSED))) willReturn Future.successful(true)
      val result: Result = await(controller.delete(Some("UNPROCESSED"))(newFakeGETRequestWithCSRF))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }

    "return 303 with query params with invalid status" in {
      given(migrationService.clear(None)) willReturn Future.successful(true)
      val result: Result = await(controller.delete(Some("other"))(newFakeGETRequestWithCSRF))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }
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
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }
  }

  "POST /reset" should {
    "return 303 when permitted" in {
      given(appConfig.resetPermitted) willReturn true
      given(migrationService.resetEnvironment()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      val result: Result = await(controller.resetConfirm()(newFakeGETRequestWithCSRF))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
      verify(migrationService).resetEnvironment()(any[HeaderCarrier])
    }

    "return 303 when not permitted" in {
      given(appConfig.resetPermitted) willReturn false
      val result: Result = await(controller.resetConfirm()(newFakeGETRequestWithCSRF))

      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
      verify(migrationService, never()).resetEnvironment()(any[HeaderCarrier])
    }
  }

  private def newFakeGETRequestWithCSRF: FakeRequest[AnyContentAsEmpty.type] = {
    val tokenProvider: TokenProvider = fakeApplication.injector.instanceOf[TokenProvider]
    val csrfTags = Map(Token.NameRequestTag -> "csrfToken", Token.RequestTag -> tokenProvider.generateToken)
    FakeRequest("GET", "/", FakeHeaders(), AnyContentAsEmpty, tags = csrfTags)
  }

  private def locationOf(result: Result): Option[String] = {
    result.header.headers.get(LOCATION)
  }

}