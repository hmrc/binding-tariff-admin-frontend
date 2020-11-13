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
import play.api.http.Status.OK
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService

class IndexControllerControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val migrationService = mock[DataMigrationService]
  private val controller       = new IndexController(new SuccessfulAuthenticatedAction, mcc, messageApi, mockAppConfig)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(mockAppConfig.analyticsToken) willReturn "token"
    given(mockAppConfig.analyticsHost) willReturn "host"
    given(mockAppConfig.resetPermitted) willReturn false
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.reset(migrationService, mockAppConfig)
  }

  "GET /" should {
    "return 200" in {
      val result: Result = await(controller.get()(FakeRequest()))
      status(result) shouldBe OK
      bodyOf(result) should include("index-heading")
    }
  }

}
