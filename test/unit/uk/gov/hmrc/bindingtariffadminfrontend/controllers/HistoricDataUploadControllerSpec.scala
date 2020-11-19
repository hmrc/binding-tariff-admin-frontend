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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import controllers.Assets.{BAD_REQUEST, SEE_OTHER}
import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status.OK
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsJson, MultipartFormData, Result}
import play.api.test.CSRFTokenHelper.CSRFFRequestHeader
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.bindingtariffadminfrontend.connector.DataMigrationJsonConnector
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileUploaded, UploadHistoricDataRequest}
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http._

import scala.concurrent.Future

class HistoricDataUploadControllerSpec extends ControllerSpec with BeforeAndAfterEach {
  private val migrationService   = mock[DataMigrationService]
  private val migrationConnector = mock[DataMigrationJsonConnector]
  private val controller = new HistoricDataUploadController(
    new SuccessfulAuthenticatedAction,
    migrationService,
    migrationConnector,
    mcc,
    messageApi,
    realConfig
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
        .willReturn(Future.successful(HttpResponse.apply(200, responseJson = Some(Json.obj("status" -> "processing")))))

      val result: Result = await(controller.getStatusOfHistoricDataProcessing()(newFakeRequestWithCSRF))

      status(result)     shouldBe OK
      jsonBodyOf(result) shouldBe Json.obj("status" -> "processing")
    }

    "return 400" in {
      given(migrationConnector.getStatusOfHistoricDataProcessing(any[HeaderCarrier]))
        .willReturn(
          Future.successful(HttpResponse.apply(400, responseJson = Some(Json.obj("error" -> "error while inserting"))))
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
      when(response.status).thenReturn(200)
      when(response.bodyAsSource: Source[ByteString, Any]).thenReturn(source)

      given(migrationConnector.downloadHistoricJson).willReturn(Future.successful(response))

      val result = await(controller.downloadHistoricJson()(newFakeRequestWithCSRF))

      status(result) shouldBe OK
      bodyOf(result) shouldBe "~~archive~~"
    }

    "return 400" in {
      val response: WSResponse = mock[WSResponse]
      when(response.status).thenReturn(400)

      given(migrationConnector.downloadHistoricJson).willReturn(Future.successful(response))

      intercept[BadRequestException](
        await(controller.downloadHistoricJson()(newFakeRequestWithCSRF))
      )
    }
  }

  "POST /" should {
    "Upload" in {
      given(migrationService.upload(any[UploadHistoricDataRequest], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .successful(())

      val f              = aForm(filename = "filename", mimeType = "text/plain")
      val result: Result = await(controller.post(newFakePOSTRequestWithCSRF.withBody(f)))

      status(result) shouldBe 202
    }

    "Handle 4xx Errors" in {
      given(migrationService.upload(any[UploadHistoricDataRequest], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .failed(Upstream4xxResponse("error", 409, 0))

      val result: Result = await(controller.post(newFakePOSTRequestWithCSRF.withBody(aForm())))

      status(result) shouldBe 409
    }

    "Handle 5xx Errors" in {
      given(migrationService.upload(any[UploadHistoricDataRequest], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .failed(Upstream5xxResponse("error", 500, 0))

      val result: Result = await(controller.post(newFakePOSTRequestWithCSRF.withBody(aForm())))

      status(result) shouldBe 502
    }

    "Handle unknown Errors" in {
      given(migrationService.upload(any[UploadHistoricDataRequest], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .failed(new RuntimeException("error"))

      val result: Result = await(controller.post(newFakePOSTRequestWithCSRF.withBody(aForm())))

      status(result) shouldBe 500
    }
  }

  "postDataAndRedirect /" should {
    val historicDataFileIds = List(
      "ALLAPPLDATA-2004_csv",
      "ALLAPPLDATA-2005_csv",
      "ALLAPPLDATA-2006_csv",
      "ALLAPPLDATA-2007_csv",
      "ALLAPPLDATA-2008_csv",
      "ALLAPPLDATA-2009_csv",
      "ALLAPPLDATA-2010_csv",
      "ALLAPPLDATA-2011_csv",
      "ALLAPPLDATA-2012_csv",
      "ALLAPPLDATA-2013_csv",
      "ALLAPPLDATA-2014_csv",
      "ALLAPPLDATA-2015_csv",
      "ALLAPPLDATA-2016_csv",
      "ALLAPPLDATA-2017_csv",
      "ALLAPPLDATA-2018_csv",
      "ALLBTIDATA-2004-1_csv",
      "ALLBTIDATA-2004-2_csv",
      "ALLBTIDATA-2004-3_csv",
      "ALLBTIDATA-2004-4_csv",
      "ALLBTIDATA-2004-5_csv",
      "ALLBTIDATA-2005_csv",
      "ALLBTIDATA-2006_csv",
      "ALLBTIDATA-2007_csv",
      "ALLBTIDATA-2008_csv",
      "ALLBTIDATA-2009_csv",
      "ALLBTIDATA-2010_csv",
      "ALLBTIDATA-2011_csv",
      "ALLBTIDATA-2012_csv",
      "ALLBTIDATA-2013_csv",
      "ALLBTIDATA-2014_csv",
      "ALLBTIDATA-2015_csv",
      "ALLBTIDATA-2016_csv",
      "ALLBTIDATA-2017_csv",
      "ALLBTIDATA-2018_csv"
    )

    val successfullyUploadedFiles: List[FileUploaded] = List(
      FileUploaded("ALLAPPLDATA-2010_csv", "ALLAPPLDATA-2010.txt", "text/plain", None, None),
      FileUploaded("ALLBTIDATA-2004_csv", "ALLBTIDATA-2004.txt", "text/plain", None, None)
    )

    "return 300 when passed valid request" in {
      given(migrationService.getAvailableFileDetails(refEq(historicDataFileIds))(any[HeaderCarrier]))
        .willReturn(Future.successful(successfullyUploadedFiles))
      given(migrationConnector.sendHistoricDataForProcessing(refEq(successfullyUploadedFiles))(any[HeaderCarrier]))
        .willReturn(Future.successful(HttpResponse.apply(202)))

      val result: Result = await(controller.initiateProcessing()(fakeUploadFileRequest(successfullyUploadedFiles)))

      status(result) shouldBe SEE_OTHER

      verify(migrationService, atLeastOnce()).getAvailableFileDetails(refEq(historicDataFileIds))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce())
        .sendHistoricDataForProcessing(refEq(successfullyUploadedFiles))(any[HeaderCarrier])
    }

    "Handle 4xx Errors from service" in {
      when(migrationService.getAvailableFileDetails(refEq(historicDataFileIds))(any[HeaderCarrier]))
        .thenReturn(Future.failed(Upstream4xxResponse("error", 409, 0)))

      intercept[Upstream4xxResponse] {
        await(controller.initiateProcessing()(fakeUploadFileRequest(Nil)))
      }

      verify(migrationService, atLeastOnce()).getAvailableFileDetails(refEq(historicDataFileIds))(any[HeaderCarrier])
      verify(migrationConnector, never())
        .sendHistoricDataForProcessing(refEq(successfullyUploadedFiles))(any[HeaderCarrier])
    }

    "Handle 5xx Errors from connector" in {
      given(migrationService.getAvailableFileDetails(refEq(historicDataFileIds))(any[HeaderCarrier]))
        .willReturn(Future.successful(successfullyUploadedFiles))
      given(migrationConnector.sendHistoricDataForProcessing(refEq(successfullyUploadedFiles))(any[HeaderCarrier]))
        .willReturn(Future.failed(Upstream5xxResponse("error", 500, 0)))

      intercept[Upstream5xxResponse] {
        await(controller.initiateProcessing()(fakeUploadFileRequest(Nil)))
      }

      verify(migrationService, atLeastOnce()).getAvailableFileDetails(refEq(historicDataFileIds))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce())
        .sendHistoricDataForProcessing(refEq(successfullyUploadedFiles))(any[HeaderCarrier])
    }

    "Handle unknown Errors from connector" in {
      given(migrationService.getAvailableFileDetails(refEq(historicDataFileIds))(any[HeaderCarrier]))
        .willReturn(Future.successful(successfullyUploadedFiles))
      given(migrationConnector.sendHistoricDataForProcessing(refEq(successfullyUploadedFiles))(any[HeaderCarrier]))
        .willReturn(Future.failed(new RuntimeException("error")))

      given(migrationConnector.sendHistoricDataForProcessing(refEq(successfullyUploadedFiles))(any[HeaderCarrier]))
        .willReturn(Future.successful(HttpResponse.apply(BAD_REQUEST)))

      intercept[RuntimeException] {
        await(controller.initiateProcessing()(fakeUploadFileRequest(Nil)))
      }

      verify(migrationService, atLeastOnce()).getAvailableFileDetails(refEq(historicDataFileIds))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce())
        .sendHistoricDataForProcessing(refEq(successfullyUploadedFiles))(any[HeaderCarrier])
    }

    "Handle the case where no files are found" in {
      given(migrationService.getAvailableFileDetails(refEq(historicDataFileIds))(any[HeaderCarrier]))
        .willReturn(Future.successful(Nil))

      intercept[java.util.NoSuchElementException] {
        await(controller.initiateProcessing()(fakeUploadFileRequest(successfullyUploadedFiles)))
      }

      verify(migrationService, atLeastOnce()).getAvailableFileDetails(refEq(historicDataFileIds))(any[HeaderCarrier])
      verify(migrationConnector, never()).sendHistoricDataForProcessing(any[List[FileUploaded]])(any[HeaderCarrier])
    }
  }

  private def aForm(filename: String = "file.txt", mimeType: String = "text/html"): MultipartFormData[TemporaryFile] = {
    val file     = SingletonTemporaryFileCreator.create(filename)
    val filePart = FilePart[TemporaryFile](key = "file", filename, contentType = Some(mimeType), ref = file)
    MultipartFormData[TemporaryFile](
      dataParts = Map("id" -> Seq(filename), "filename" -> Seq(filename), "mimetype" -> Seq(mimeType)),
      files     = Seq(filePart),
      badParts  = Seq.empty
    )
  }

  private def fakeUploadFileRequest(uploadedFiles: List[FileUploaded]): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest("GET", "/", FakeHeaders(), AnyContentAsJson(Json.toJson(uploadedFiles))).withCSRFToken
      .asInstanceOf[FakeRequest[AnyContentAsEmpty.type]]
}
