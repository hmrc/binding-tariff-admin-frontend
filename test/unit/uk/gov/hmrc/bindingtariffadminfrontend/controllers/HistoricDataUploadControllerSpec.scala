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

import java.util.UUID

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status._
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.libs.json.{Format, Json}
import play.api.libs.ws.WSResponse
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsEmpty, MultipartFormData, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtariffadminfrontend.connector.DataTransformationConnector
import uk.gov.hmrc.bindingtariffadminfrontend.forms.{InitiateHistoricDataFormProvider, UploadHistoricDataFormProvider}
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.FileUploaded
import uk.gov.hmrc.bindingtariffadminfrontend.model.{HistoricDataUpload, InitiateHistoricDataProcessing}
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http._

import scala.concurrent.Future

class HistoricDataUploadControllerSpec extends ControllerSpec with BeforeAndAfterEach {
  private val migrationService   = mock[DataMigrationService]
  private val migrationConnector = mock[DataTransformationConnector]

  private val controller = new HistoricDataUploadController(
    authenticatedAction              = new SuccessfulAuthenticatedAction,
    service                          = migrationService,
    connector                        = migrationConnector,
    uploadHistoricDataFormProvider   = new UploadHistoricDataFormProvider,
    initiateHistoricDataFormProvider = new InitiateHistoricDataFormProvider,
    mcc                              = mcc,
    messagesApi                      = messageApi,
    appConfig                        = realConfig
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(migrationService, migrationConnector)
  }

  "GET /" should {
    "return 200" in {
      val result: Result = await(controller.get()(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("historic_data_migration_upload-heading")
    }
  }

  "checkHistoricStatus /" should {
    "return 200" in {
      val result: Result = await(controller.checkHistoricStatus()(newFakeRequestWithCSRF))
      status(result) shouldBe OK
    }
  }

  "getStatusOfHistoricDataProcessing /" should {
    "return 200" in {
      given(migrationConnector.getStatusOfHistoricDataProcessing(any[HeaderCarrier]))
        .willReturn(Future.successful(HttpResponse.apply(OK, responseJson = Some(Json.obj("status" -> "processing")))))

      val result: Result = await(controller.getStatusOfHistoricDataProcessing()(newFakeRequestWithCSRF))

      status(result)     shouldBe OK
      jsonBodyOf(result) shouldBe Json.obj("status" -> "processing")
    }

    "return 400" in {
      given(migrationConnector.getStatusOfHistoricDataProcessing(any[HeaderCarrier]))
        .willReturn(
          Future.successful(
            HttpResponse.apply(BAD_REQUEST, responseJson = Some(Json.obj("error" -> "error while inserting")))
          )
        )

      val result: Result = await(controller.getStatusOfHistoricDataProcessing()(newFakeRequestWithCSRF))

      status(result)     shouldBe BAD_REQUEST
      jsonBodyOf(result) shouldBe Json.obj("error" -> "error while inserting")
    }
  }

  "downloadHistoricJson /" should {
    "return 200" in {
      val source               = Source.single(ByteString.fromString("~~archive~~"))
      val response: WSResponse = mock[WSResponse]
      when(response.status).thenReturn(OK)
      when(response.bodyAsSource: Source[ByteString, Any]).thenReturn(source)

      given(migrationConnector.downloadHistoricJson).willReturn(Future.successful(response))

      val result = await(controller.downloadHistoricJson()(newFakeRequestWithCSRF))

      status(result) shouldBe OK
      bodyOf(result) shouldBe "~~archive~~"
    }

    "return 400" in {
      val response: WSResponse = mock[WSResponse]
      when(response.status).thenReturn(BAD_REQUEST)

      given(migrationConnector.downloadHistoricJson).willReturn(Future.successful(response))

      intercept[BadRequestException](
        await(controller.downloadHistoricJson()(newFakeRequestWithCSRF))
      )
    }
  }

  "POST /" should {
    "Upload" in {
      given(migrationService.upload(any[HistoricDataUpload], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .successful(())

      val result: Result = await(controller.post(fakeUploadRequest()))

      status(result) shouldBe ACCEPTED
    }

    "Handle 4xx Errors" in {
      given(migrationService.upload(any[HistoricDataUpload], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .failed(Upstream4xxResponse("error", CONFLICT, 0))

      val result: Result = await(controller.post(fakeUploadRequest()))

      status(result) shouldBe CONFLICT
    }

    "Handle 5xx Errors" in {
      given(migrationService.upload(any[HistoricDataUpload], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .failed(Upstream5xxResponse("error", INTERNAL_SERVER_ERROR, 0))

      val result: Result = await(controller.post(fakeUploadRequest()))

      status(result) shouldBe BAD_GATEWAY
    }

    "Handle unknown Errors" in {
      given(migrationService.upload(any[HistoricDataUpload], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .failed(new RuntimeException("error"))

      val result: Result = await(controller.post(fakeUploadRequest()))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "initiateProcessing /" should {

    val successfullyUploadedFiles: List[FileUploaded] = List(
      FileUploaded(UUID.randomUUID().toString, "ALLAPPLDATA-2010.txt", "text/plain"),
      FileUploaded(UUID.randomUUID().toString, "ALLBTIDATA-2004.txt", "text/plain")
    )

    "return 300 when passed valid request" in {
      given(migrationService.getUploadedBatch(refEq("batchId"))(any[HeaderCarrier]))
        .willReturn(Future.successful(successfullyUploadedFiles))
      given(migrationConnector.sendHistoricDataForProcessing(refEq(successfullyUploadedFiles))(any[HeaderCarrier]))
        .willReturn(Future.successful(HttpResponse.apply(ACCEPTED)))

      val result: Result = await(controller.initiateProcessing()(fakeInitiateRequest()))

      status(result) shouldBe SEE_OTHER

      verify(migrationService, atLeastOnce()).getUploadedBatch(refEq("batchId"))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce())
        .sendHistoricDataForProcessing(refEq(successfullyUploadedFiles))(any[HeaderCarrier])
    }

    "return 400 when passed an invalid request" in {
      val result: Result = await(controller.initiateProcessing()(newFakeGETRequestWithCSRF))

      status(result) shouldBe BAD_REQUEST

      verify(migrationService, never()).getUploadedBatch(any[String])(any[HeaderCarrier])
      verify(migrationConnector, never())
        .sendHistoricDataForProcessing(any[List[FileUploaded]])(any[HeaderCarrier])
    }

    "Handle 4xx Errors from service" in {
      when(migrationService.getUploadedBatch(refEq("batchId"))(any[HeaderCarrier]))
        .thenReturn(Future.failed(Upstream4xxResponse("error", CONFLICT, 0)))

      intercept[Upstream4xxResponse] {
        await(controller.initiateProcessing()(fakeInitiateRequest()))
      }

      verify(migrationService, atLeastOnce()).getUploadedBatch(refEq("batchId"))(any[HeaderCarrier])
      verify(migrationConnector, never())
        .sendHistoricDataForProcessing(any[List[FileUploaded]])(any[HeaderCarrier])
    }

    "Handle 5xx Errors from connector" in {
      given(migrationService.getUploadedBatch(refEq("batchId"))(any[HeaderCarrier]))
        .willReturn(Future.successful(successfullyUploadedFiles))
      given(migrationConnector.sendHistoricDataForProcessing(refEq(successfullyUploadedFiles))(any[HeaderCarrier]))
        .willReturn(Future.failed(Upstream5xxResponse("error", INTERNAL_SERVER_ERROR, 0)))

      intercept[Upstream5xxResponse] {
        await(controller.initiateProcessing()(fakeInitiateRequest()))
      }

      verify(migrationService, atLeastOnce()).getUploadedBatch(refEq("batchId"))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce())
        .sendHistoricDataForProcessing(refEq(successfullyUploadedFiles))(any[HeaderCarrier])
    }

    "Handle unknown Errors from connector" in {
      given(migrationService.getUploadedBatch(refEq("batchId"))(any[HeaderCarrier]))
        .willReturn(Future.successful(successfullyUploadedFiles))
      given(migrationConnector.sendHistoricDataForProcessing(refEq(successfullyUploadedFiles))(any[HeaderCarrier]))
        .willReturn(Future.failed(new RuntimeException("error")))

      given(migrationConnector.sendHistoricDataForProcessing(refEq(successfullyUploadedFiles))(any[HeaderCarrier]))
        .willReturn(Future.successful(HttpResponse.apply(BAD_REQUEST)))

      intercept[RuntimeException] {
        await(controller.initiateProcessing()(fakeInitiateRequest()))
      }

      verify(migrationService, atLeastOnce()).getUploadedBatch(refEq("batchId"))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce())
        .sendHistoricDataForProcessing(refEq(successfullyUploadedFiles))(any[HeaderCarrier])
    }
  }

  private def fakeInitiateRequest(batchId: String = "batchId"): FakeRequest[AnyContentAsEmpty.type] = {
    implicit val formats: Format[InitiateHistoricDataProcessing] = Json.format[InitiateHistoricDataProcessing]

    newFakeGETRequestWithCSRF
      .withJsonBody(Json.toJson(InitiateHistoricDataProcessing(batchId)))
      .asInstanceOf[FakeRequest[AnyContentAsEmpty.type]]
  }

  private def fakeUploadRequest(
    filename: String = "file.txt",
    mimeType: String = "text/html",
    batchId: String  = "batchId"
  ): FakeRequest[MultipartFormData[TemporaryFile]] = {
    def form: MultipartFormData[TemporaryFile] = {
      val file     = SingletonTemporaryFileCreator.create(filename)
      val filePart = FilePart[TemporaryFile](key = "file", filename, contentType = Some(mimeType), ref = file)
      MultipartFormData[TemporaryFile](
        dataParts = Map("filename" -> Seq(filename), "mimetype" -> Seq(mimeType), "batchId" -> Seq(batchId)),
        files     = Seq(filePart),
        badParts  = Seq.empty
      )
    }

    newFakePOSTRequestWithCSRF.withBody(form)
  }
}
