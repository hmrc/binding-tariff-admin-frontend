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

import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.OK
import play.api.i18n.{DefaultLangs, DefaultMessagesApi}
import play.api.test.FakeRequest
import play.api.{Configuration, Environment}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.play.test.UnitSpec

class HelloWorldControllerSpec extends WordSpec with Matchers with UnitSpec with GuiceOneAppPerSuite {

  private val fakeRequest = FakeRequest()

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)

  private val messageApi = new DefaultMessagesApi(env, configuration, new DefaultLangs(configuration))
  private val appConfig = new AppConfig(configuration, env)

  private val controller = new HelloWorld(messageApi, appConfig)

  "GET /" should {

    "return 200" in {
      val result = await(controller.helloWorld()(fakeRequest))
      status(result) shouldBe OK
    }

//    "return HTML" in {
//      val result = controller.helloWorld(fakeRequest)
//      contentType(result) shouldBe Some("text/html")
//      charset(result) shouldBe Some("utf-8")
//    }
  }

}