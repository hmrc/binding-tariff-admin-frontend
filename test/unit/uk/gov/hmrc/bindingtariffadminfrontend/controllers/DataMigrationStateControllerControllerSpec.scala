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
import org.mockito.BDDMockito.given
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
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
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class DataMigrationStateControllerControllerSpec extends WordSpec with Matchers with UnitSpec with MockitoSugar with GuiceOneAppPerSuite {

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)
  private val migrationService = mock[DataMigrationService]
  private val messageApi = new DefaultMessagesApi(env, configuration, new DefaultLangs(configuration))
  private val appConfig = new AppConfig(configuration, env)
  private implicit val mat: Materializer = app.materializer
  private val controller = new DataMigrationStateController(migrationService, messageApi, appConfig)

  "GET /state" should {
    "return 200 when not in progress" in {
      given(migrationService.counts) willReturn Future.successful(new MigrationCounts(Map()))
      given(migrationService.getState) willReturn Future.successful(Seq.empty)
      val result: Result = await(controller.get()(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("Upload")
    }

    "return 200 when in progress" in {
      given(migrationService.counts) willReturn Future.successful(new MigrationCounts(Map(MigrationStatus.UNPROCESSED -> 1)))
      given(migrationService.getState) willReturn Future.successful(Seq.empty)
      val result: Result = await(controller.get()(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("In Progress")
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

  private def newFakeGETRequestWithCSRF: FakeRequest[AnyContentAsEmpty.type] = {
    val tokenProvider: TokenProvider = app.injector.instanceOf[TokenProvider]
    val csrfTags = Map(Token.NameRequestTag -> "csrfToken", Token.RequestTag -> tokenProvider.generateToken)
    FakeRequest("GET", "/", FakeHeaders(), AnyContentAsEmpty, tags = csrfTags)
  }

  private def locationOf(result: Result): Option[String] = {
    result.header.headers.get(LOCATION)
  }

}