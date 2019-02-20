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
import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito.given
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.OK
import play.api.i18n.{DefaultLangs, DefaultMessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsJson, Result}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.{Configuration, Environment}
import play.filters.csrf.CSRF.{Token, TokenProvider}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{UploadRequest, UploadTemplate}
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class FileMigrationUploadControllerSpec extends WordSpec with Matchers
  with UnitSpec with MockitoSugar with GuiceOneAppPerSuite {

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)
  private val migrationService = mock[DataMigrationService]
  private val messageApi = new DefaultMessagesApi(env, configuration, new DefaultLangs(configuration))
  private val appConfig = new AppConfig(configuration, env)
  private implicit val mat: Materializer = app.materializer
  private val controller = new FileMigrationUploadController(
    new SuccessfulAuthenticatedAction, migrationService, messageApi, appConfig
  )

  "GET /" should {
    "return 200" in {
      val result: Result = await(controller.get()(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("file_migration_upload-heading")
    }
  }

  "POST /" should {
    val request = UploadRequest(
      fileName = "filename",
      mimeType = "text/plain"
    )

    val template = UploadTemplate(
      href = "href",
      fields = Map()
    )

    "Initiate Upload" in {
      given(migrationService.initiateFileMigration(refEq(request))(any[HeaderCarrier])) willReturn Future.successful(template)


      val body = Json.toJson(request)
      val result: Result = await(controller.post(newFakePOSTRequestWithCSRF.withBody(body)))

      status(result) shouldBe OK
      jsonBodyOf(result).as[UploadTemplate] shouldBe template
    }

    "Handle 4xx Errors" in {
      given(migrationService.initiateFileMigration(refEq(request))(any[HeaderCarrier])) willReturn Future.failed(Upstream4xxResponse("error", 409, 0))

      val body = Json.toJson(request)
      val result: Result = await(controller.post(newFakePOSTRequestWithCSRF.withBody(body)))

      status(result) shouldBe 409
    }

    "Handle 5xx Errors" in {
      given(migrationService.initiateFileMigration(refEq(request))(any[HeaderCarrier])) willReturn Future.failed(Upstream5xxResponse("error", 500, 0))

      val body = Json.toJson(request)
      val result: Result = await(controller.post(newFakePOSTRequestWithCSRF.withBody(body)))

      status(result) shouldBe 502
    }

    "Handle unknown Errors" in {
      given(migrationService.initiateFileMigration(refEq(request))(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("error"))

      val body = Json.toJson(request)
      val result: Result = await(controller.post(newFakePOSTRequestWithCSRF.withBody(body)))

      status(result) shouldBe 500
    }

  }

  private def newFakeGETRequestWithCSRF: FakeRequest[AnyContentAsEmpty.type] = {
    val tokenProvider: TokenProvider = app.injector.instanceOf[TokenProvider]
    val csrfTags = Map(Token.NameRequestTag -> "csrfToken", Token.RequestTag -> tokenProvider.generateToken)
    FakeRequest("GET", "/", FakeHeaders(), AnyContentAsEmpty, tags = csrfTags)
  }

  private def newFakePOSTRequestWithCSRF: FakeRequest[AnyContentAsJson.type] = {
    val tokenProvider: TokenProvider = app.injector.instanceOf[TokenProvider]
    val csrfTags = Map(Token.NameRequestTag -> "csrfToken", Token.RequestTag -> tokenProvider.generateToken)
    FakeRequest("POST", "/", FakeHeaders(), AnyContentAsJson, tags = csrfTags)
  }

}
