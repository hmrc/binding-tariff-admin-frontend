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
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import play.api.i18n.{DefaultLangs, DefaultMessagesApi, Messages}
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.{Configuration, Environment}
import play.filters.csrf.CSRF.{Token, TokenProvider}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.AuthenticatedRequest
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

abstract class ControllerSpec extends WordSpec
  with Matchers
  with UnitSpec
  with MockitoSugar
  with WithFakeApplication
  with BeforeAndAfterEach {

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)
  protected implicit val messageApi: DefaultMessagesApi = new DefaultMessagesApi(env, configuration, new DefaultLangs(configuration))
  protected implicit val appConfig: AppConfig = mock[AppConfig]
  protected implicit val mat: Materializer = fakeApplication.materializer

  protected def authenticatedRequest(request: Request[AnyContentAsEmpty.type]): AuthenticatedRequest[AnyContentAsEmpty.type] = AuthenticatedRequest("operator", request)
  protected def messages: Messages =  messageApi.preferred(newFakeGETRequestWithCSRF)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(appConfig.analyticsToken) willReturn "token"
    given(appConfig.analyticsHost) willReturn "host"
  }

  protected def newFakeGETRequestWithCSRF: FakeRequest[AnyContentAsEmpty.type] = {
    val tokenProvider: TokenProvider = fakeApplication.injector.instanceOf[TokenProvider]
    val csrfTags = Map(Token.NameRequestTag -> "csrfToken", Token.RequestTag -> tokenProvider.generateToken)
    FakeRequest("GET", "/", FakeHeaders(), AnyContentAsEmpty, tags = csrfTags)
  }

  protected def newFakePOSTRequestWithCSRF: FakeRequest[AnyContentAsEmpty.type] = {
    val tokenProvider: TokenProvider = fakeApplication.injector.instanceOf[TokenProvider]
    val csrfTags = Map(Token.NameRequestTag -> "csrfToken", Token.RequestTag -> tokenProvider.generateToken)
    FakeRequest("POST", "/", FakeHeaders(), AnyContentAsEmpty, tags = csrfTags)
  }

}
