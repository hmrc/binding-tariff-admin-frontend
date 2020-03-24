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
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import play.api.http.Status._
import play.api.i18n.{DefaultLangs, DefaultMessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.libs.ws.{DefaultWSResponseHeaders, StreamedResponse}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsEmpty, MultipartFormData, Result}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.{Configuration, Environment}
import play.filters.csrf.CSRF.{Token, TokenProvider}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.connector.DataMigrationJsonConnector
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.FileUploaded
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http._
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

  val aSuccessfullyUploadedFile: FileUploaded = FileUploaded("name", "published", "text/plain", None, None)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(migrationService, migrationConnector)
  }

  "getAnonymiseData /" should {

    "return 200" in {

      val result: Result = await(controller.getAnonymiseData()(newFakeRequestWithCSRF))

      status(result) shouldBe OK
    }
  }

  "anonymiseData /" should {

    "return 200" in {

      val filename: String = "file.txt"
      val mimeType: String = "application/csv"
      val data : MultipartFormData[TemporaryFile] = {
        val file = TemporaryFile(filename)
        val filePart = FilePart[TemporaryFile](key = "file", filename, contentType = Some(mimeType), ref = file)
        MultipartFormData[TemporaryFile](
          dataParts = Map("id" -> Seq(filename), "filename" -> Seq(filename), "mimetype" -> Seq(mimeType)),
          files = Seq(filePart),
          badParts = Seq.empty
        )
      }

      val result = await(controller.anonymiseData()(newFakeRequestWithCSRF.withBody(data)))

      status(result) shouldBe OK

    }

    "return 400" in {
      val data : MultipartFormData[TemporaryFile] = {
        MultipartFormData[TemporaryFile](
          dataParts = Map("id" -> Seq.empty, "filename" -> Seq.empty, "mimetype" -> Seq.empty),
          files = Seq.empty,
          badParts = Seq.empty
        )
      }

      val result = await(controller.anonymiseData()(newFakeRequestWithCSRF.withBody(data)))

      status(result) shouldBe BAD_REQUEST

    }
  }

  "postDataAndRedirect /" should {

    "return 300" in {
      given(migrationService.getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier]))
        .willReturn(Future.successful(List[FileUploaded](aSuccessfullyUploadedFile)))
      given(migrationConnector.sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier]))
        .willReturn(Future.successful(HttpResponse.apply(202)))

      val result: Result = await(controller.postDataAndRedirect()(newFakeRequestWithCSRF))

      status(result) shouldBe SEE_OTHER

      verify(migrationService, atLeastOnce()).getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce()).sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier])
    }

    "Handle 4xx Errors from service" in {
      when(migrationService.getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])).
        thenReturn(Future.failed(Upstream4xxResponse("error", 409, 0)))

      intercept[Upstream4xxResponse] {
        await(controller.postDataAndRedirect()(newFakeRequestWithCSRF))
      }

      verify(migrationService, atLeastOnce()).getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])
      verify(migrationConnector, never()).sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier])
    }

    "Handle 5xx Errors from connector" in {
      given(migrationService.getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier]))
        .willReturn(Future.successful(List[FileUploaded](aSuccessfullyUploadedFile)))
      given(migrationConnector.sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier]))
        .willReturn(Future.failed(Upstream5xxResponse("error", 500, 0)))

      intercept[Upstream5xxResponse] {
        await(controller.postDataAndRedirect()(newFakeRequestWithCSRF))
      }

      verify(migrationService, atLeastOnce()).getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce()).sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier])
    }

    "Handle unknown Errors from connector" in {
      given(migrationService.getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier]))
        .willReturn(Future.successful(List[FileUploaded](aSuccessfullyUploadedFile)))
      given(migrationConnector.sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier]))
        .willReturn(Future.failed(new RuntimeException("error")))

      given(migrationConnector.sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier]))
        .willReturn(Future.successful(HttpResponse.apply(BAD_REQUEST)))

      intercept[RuntimeException] {
        await(controller.postDataAndRedirect()(newFakeRequestWithCSRF))
      }

      verify(migrationService, atLeastOnce()).getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce()).sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier])
    }
  }

  "checkStatus /" should {

    "return 200" in {

      val result: Result = await(controller.checkStatus()(newFakeRequestWithCSRF))

      status(result) shouldBe OK
    }
  }

  "getStatusOfJsonProcessing /" should {

    "return 200" in {
      given(migrationConnector.getStatusOfJsonProcessing(any[HeaderCarrier]))
        .willReturn(Future.successful(HttpResponse.apply(200, responseJson= Some(Json.obj("status" -> "inserting")))))

      val result: Result = await(controller.getStatusOfJsonProcessing()(newFakeRequestWithCSRF))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.obj("status" -> "inserting")
    }

    "return 400" in {
      given(migrationConnector.getStatusOfJsonProcessing(any[HeaderCarrier]))
        .willReturn(Future.successful(HttpResponse.apply(400, responseJson= Some(Json.obj("error" -> "error while inserting")))))

      val result: Result = await(controller.getStatusOfJsonProcessing()(newFakeRequestWithCSRF))

      status(result) shouldBe BAD_REQUEST
      jsonBodyOf(result) shouldBe Json.obj("error" -> "error while inserting")
    }
  }

  "downloadJson /" should {

    "return 200" in {
      val json = Json.parse("""{
                              |  "href": "url",
                              |  "fields": {
                              |    "field": "value"
                              |  }
                              |}""".stripMargin)

      val response = StreamedResponse.apply(
        DefaultWSResponseHeaders(200, Map.empty), body= Source.apply(List(ByteString(json.toString()))))
      given(migrationConnector.downloadJson).willReturn(Future.successful(response))

      val result = await(controller.downloadJson()(newFakeRequestWithCSRF))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.parse("""[{
                                               |  "href": "url",
                                               |  "fields": {
                                               |    "field": "value"
                                               |  }
                                               |}]""".stripMargin)

    }

    "return 400" in {
      val response = StreamedResponse.apply(
        DefaultWSResponseHeaders(400, Map.empty), body= Source.apply(
          List(ByteString(Json.obj("error" -> "error while building josn").toString()))))
      given(migrationConnector.downloadJson).willReturn(Future.successful(response))

      intercept[BadRequestException](
        await(controller.downloadJson()(newFakeRequestWithCSRF))
      )
    }
  }

  private def newFakeRequestWithCSRF: FakeRequest[AnyContentAsEmpty.type] = {
    val tokenProvider: TokenProvider = fakeApplication.injector.instanceOf[TokenProvider]
    val csrfTags = Map(Token.NameRequestTag -> "csrfToken", Token.RequestTag -> tokenProvider.generateToken)
    FakeRequest("GET", "/", FakeHeaders(), AnyContentAsEmpty, tags = csrfTags)
  }

}
