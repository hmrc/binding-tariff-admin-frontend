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

import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterEach
import play.api.http.HeaderNames.LOCATION
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.Result
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService

import scala.concurrent.Future

class DataMigrationStateControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val migrationService = mock[DataMigrationService]
  private val appConfig        = mock[AppConfig]

  private val controller = new DataMigrationStateController(
    authenticatedAction = new SuccessfulAuthenticatedAction,
    service             = migrationService,
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
    Mockito.reset(migrationService, appConfig)
  }

  "GET /state" should {
    "return 200 when not in progress" in {
      given(migrationService.counts) willReturn Future.successful(new MigrationCounts(Map()))
      given(migrationService.getState(Seq(MigrationStatus.SUCCESS), Pagination(0, 1))) willReturn Future.successful(
        Paged.empty[Migration]
      )
      val result: Result = await(controller.get(0, Seq(MigrationStatus.SUCCESS.toString))(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("data_migration_state-complete-heading")
    }

    "return 200 when in progress" in {
      given(migrationService.counts) willReturn Future.successful(
        new MigrationCounts(Map(MigrationStatus.UNPROCESSED -> 1))
      )
      given(migrationService.getState(Seq(MigrationStatus.SUCCESS), Pagination(0, 1))) willReturn Future.successful(
        Paged.empty[Migration]
      )
      val result: Result = await(controller.get(0, Seq(MigrationStatus.SUCCESS.toString))(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("data_migration_state-in_progress-heading")
    }

    "paginates" in {
      given(migrationService.counts) willReturn Future.successful(new MigrationCounts(Map()))
      given(migrationService.getState(Seq(MigrationStatus.SUCCESS), Pagination(0, 1))) willReturn Future.successful(
        Paged.empty[Migration]
      )
      val result: Result = await(controller.get(0, Seq(MigrationStatus.SUCCESS.toString))(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
    }
  }

  "DELETE /state" should {
    "return 303" in {
      given(migrationService.clear(None)) willReturn Future.successful(true)
      val result: Result = await(controller.delete(None)(newFakeGETRequestWithCSRF))
      status(result)     shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }

    "return 303 with query params" in {
      given(migrationService.clear(Some(MigrationStatus.UNPROCESSED))) willReturn Future.successful(true)
      val result: Result = await(controller.delete(Some("UNPROCESSED"))(newFakeGETRequestWithCSRF))
      status(result)     shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }

    "return 303 with query params with invalid status" in {
      given(migrationService.clear(None)) willReturn Future.successful(true)
      val result: Result = await(controller.delete(Some("other"))(newFakeGETRequestWithCSRF))
      status(result)     shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }
  }

  private def locationOf(result: Result): Option[String] =
    result.header.headers.get(LOCATION)

}
