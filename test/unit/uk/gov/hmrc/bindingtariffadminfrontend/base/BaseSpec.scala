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

package uk.gov.hmrc.bindingtariffadminfrontend.base

import akka.stream.Materializer
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.i18n.MessagesApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{BodyParsers, MessagesControllerComponents}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.connector.ResourceFiles
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec

abstract class BaseSpec
  extends UnitSpec
    with GuiceOneAppPerSuite
    with MockitoSugar
    with ResourceFiles
    with BeforeAndAfterEach {

  override lazy val fakeApplication: Application = GuiceApplicationBuilder()
    .configure(
      //turn off metrics
      "metrics.jvm" -> false,
      "metrics.enabled" -> false
    ).build()

  def inject[T](implicit m: Manifest[T]): T = app.injector.instanceOf[T]

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val mat: Materializer = fakeApplication.materializer

  implicit val realConfig: AppConfig = app.injector.instanceOf[AppConfig]
  val mockAppConfig: AppConfig = mock[AppConfig]

  val defaultBodyParser: BodyParsers.Default = app.injector.instanceOf[BodyParsers.Default]
  val mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]
  val messageApi: MessagesApi = app.injector.instanceOf[MessagesApi]

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    Mockito.reset(mockAppConfig)
  }
}
