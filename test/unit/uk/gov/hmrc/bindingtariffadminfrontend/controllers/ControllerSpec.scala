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

import org.scalatest.{BeforeAndAfterEach, Matchers}
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsJson, Request}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.bindingtariffadminfrontend.base.BaseSpec
import uk.gov.hmrc.bindingtariffadminfrontend.model.AuthenticatedRequest
import play.api.test.CSRFTokenHelper._

abstract class ControllerSpec extends BaseSpec with Matchers with BeforeAndAfterEach {

  protected def authenticatedRequest(
    request: Request[AnyContentAsEmpty.type]
  ): AuthenticatedRequest[AnyContentAsEmpty.type] = AuthenticatedRequest("operator", request)

  def newFakeRequestWithCSRF: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest("GET", "/", FakeHeaders(), AnyContentAsEmpty).withCSRFToken
      .asInstanceOf[FakeRequest[AnyContentAsEmpty.type]]

  def newFakeGETRequestWithCSRF: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest("GET", "/", FakeHeaders(), AnyContentAsEmpty).withCSRFToken
      .asInstanceOf[FakeRequest[AnyContentAsEmpty.type]]

  def newFakePOSTRequestWithCSRF: FakeRequest[AnyContentAsJson.type] =
    FakeRequest("POST", "/", FakeHeaders(), AnyContentAsJson).withCSRFToken
      .asInstanceOf[FakeRequest[AnyContentAsJson.type]]

}

object ControllerSpec {

  def FakeAuthRequest(verb: String): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(verb, "/", FakeHeaders(), AnyContentAsEmpty)
}
