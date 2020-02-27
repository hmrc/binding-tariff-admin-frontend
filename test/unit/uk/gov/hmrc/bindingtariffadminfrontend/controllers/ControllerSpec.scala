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

import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.bindingtariffadminfrontend.model.AuthenticatedRequest
import uk.gov.hmrc.play.test.UnitSpec

abstract class ControllerSpec extends WordSpec
  with Matchers
  with UnitSpec
  with MockitoSugar
  with GuiceOneAppPerSuite
  with BeforeAndAfterEach {

  def inject[T](implicit m: Manifest[T]) = app.injector.instanceOf[T]

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)

  protected def authenticatedRequest(request: Request[AnyContentAsEmpty.type]): AuthenticatedRequest[AnyContentAsEmpty.type] = AuthenticatedRequest("operator", request)
}

object  ControllerSpec {

  def FakeAuthRequest(verb: String): FakeRequest[AnyContentAsEmpty.type] = {
    FakeRequest(verb, "/", FakeHeaders(), AnyContentAsEmpty)
  }
}
