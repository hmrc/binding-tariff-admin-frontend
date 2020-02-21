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

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import play.api.http.Status.OK
import play.api.i18n.{DefaultLangs, DefaultMessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.{Configuration, Environment}
import play.filters.csrf.CSRF.{Token, TokenProvider}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.connector.DataMigrationJsonConnector
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.FileUploaded
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class DataMigrationJsonControllerSpec extends WordSpec with Matchers
  with UnitSpec with MockitoSugar with WithFakeApplication with BeforeAndAfterEach {

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)
  private val migrationService = mock[DataMigrationService]
  private val migrationConnector = mock[DataMigrationJsonConnector]
  private val actorSystem = mock[ActorSystem]
  private val messageApi = new DefaultMessagesApi(env, configuration, new DefaultLangs(configuration))
  private val appConfig = new AppConfig(configuration, env)
  private implicit val mat: Materializer = fakeApplication.materializer
  private val controller = new DataMigrationJsonController(
    new SuccessfulAuthenticatedAction, migrationService, migrationConnector, actorSystem, mat, messageApi, appConfig
  )

  private val csvList = List("tblCaseClassMeth_csv", "historicCases_csv", "eBTI_Application_csv",
    "eBTI_Addresses_csv", "tblCaseRecord_csv", "tblCaseBTI_csv", "tblImages_csv",
    "tblMovement_csv", "tblSample_csv", "tblUser_csv")
  val aSuccessfullyUploadedFile = FileUploaded("name", "published", "text/plain", None, None)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(migrationService, migrationConnector)
  }

  "GET /" should {

    "return 200" in {
      given(migrationService.getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])).willReturn(Future.successful(List[FileUploaded](aSuccessfullyUploadedFile)))
      given(migrationConnector.generateJson(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier])).willReturn(Future.successful(Json.parse("""[{"file" : "uploaded"}]""")))

      val result: Result = await(controller.get()(newFakeGETRequestWithCSRF))

      status(result) shouldBe OK
      verify(migrationService, atLeastOnce()).getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce()).generateJson(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier])
    }

    "Handle 4xx Errors from service" in {
      when(migrationService.getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])).thenReturn(Future.failed(Upstream4xxResponse("error", 409, 0)))

      intercept[Upstream4xxResponse] {
        await(controller.get()(newFakeGETRequestWithCSRF))
      }

      verify(migrationService, atLeastOnce()).getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])
      verify(migrationConnector, never()).generateJson(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier])
    }

    "Handle 5xx Errors from connector" in {
      given(migrationService.getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])).willReturn(Future.successful(List[FileUploaded](aSuccessfullyUploadedFile)))
      given(migrationConnector.generateJson(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier])).willReturn(Future.failed(Upstream5xxResponse("error", 500, 0)))

      intercept[Upstream5xxResponse] {
        await(controller.get()(newFakeGETRequestWithCSRF))
      }

      verify(migrationService, atLeastOnce()).getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce()).generateJson(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier])
    }

    "Handle unknown Errors from connector" in {
      given(migrationService.getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])).willReturn(Future.successful(List[FileUploaded](aSuccessfullyUploadedFile)))
      given(migrationConnector.generateJson(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier])).willReturn(Future.failed(new RuntimeException("error")))

      intercept[RuntimeException] {
        await(controller.get()(newFakeGETRequestWithCSRF))
      }

      verify(migrationService, atLeastOnce()).getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce()).generateJson(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier])
    }
  }

  private def newFakeGETRequestWithCSRF: FakeRequest[AnyContentAsEmpty.type] = {
    val tokenProvider: TokenProvider = fakeApplication.injector.instanceOf[TokenProvider]
    val csrfTags = Map(Token.NameRequestTag -> "csrfToken", Token.RequestTag -> tokenProvider.generateToken)
    FakeRequest("GET", "/", FakeHeaders(), AnyContentAsEmpty, tags = csrfTags)
  }

}
