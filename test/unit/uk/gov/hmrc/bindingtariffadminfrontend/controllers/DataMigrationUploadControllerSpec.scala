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

import java.time.LocalDate
import java.util.UUID

import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{atLeastOnce, never, reset, verify}
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status.{OK, _}
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.libs.json.{Format, Json}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsEmpty, MultipartFormData, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtariffadminfrontend.connector.DataMigrationJsonConnector
import uk.gov.hmrc.bindingtariffadminfrontend.forms.{InitiateMigrationDataFormProvider, UploadMigrationDataFormProvider}
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileUploadSubmission, FileUploaded}
import uk.gov.hmrc.bindingtariffadminfrontend.model.{InitiateMigrationDataProcessing, MigrationDataUpload}
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, Upstream4xxResponse, Upstream5xxResponse}

import scala.concurrent.Future

class DataMigrationUploadControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val migrationService   = mock[DataMigrationService]
  private val migrationConnector = mock[DataMigrationJsonConnector]

  private val controller = new DataMigrationUploadController(
    authenticatedAction               = new SuccessfulAuthenticatedAction,
    service                           = migrationService,
    connector                         = migrationConnector,
    uploadMigrationDataFormProvider   = new UploadMigrationDataFormProvider,
    initiateMigrationDataFormProvider = new InitiateMigrationDataFormProvider,
    mcc                               = mcc,
    messagesApi                       = messageApi,
    appConfig                         = realConfig
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(migrationService, migrationConnector)
  }

  private val extractionDate = LocalDate.now

  "GET /" should {
    "return 200" in {
      val result: Result = await(controller.get()(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("data_migration_upload-heading")
    }
  }

  "POST /" should {
    "Upload" in {
      given(migrationService.upload(any[MigrationDataUpload], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .successful(())

      val result: Result = await(controller.post(fakeMigrationDataUpload()))

      status(result) shouldBe ACCEPTED
    }

    "Handle 4xx Errors" in {
      given(migrationService.upload(any[MigrationDataUpload], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .failed(Upstream4xxResponse("error", CONFLICT, 0))

      val result: Result = await(controller.post(fakeMigrationDataUpload()))

      status(result) shouldBe CONFLICT
    }

    "Handle 5xx Errors" in {
      given(migrationService.upload(any[MigrationDataUpload], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .failed(Upstream5xxResponse("error", INTERNAL_SERVER_ERROR, 0))

      val result: Result = await(controller.post(fakeMigrationDataUpload()))

      status(result) shouldBe BAD_GATEWAY
    }

    "Handle unknown Errors" in {
      given(migrationService.upload(any[MigrationDataUpload], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .failed(new RuntimeException("error"))

      val result: Result = await(controller.post(fakeMigrationDataUpload()))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "initiateProcessing /" should {

    val successfullyUploadedFiles: List[FileUploaded] = List(
      FileUploaded(UUID.randomUUID().toString, "tblCaseRecord.txt", "text/plain"),
      FileUploaded(UUID.randomUUID().toString, "tblMovement.txt", "text/plain")
    )

    val uploadSubmission = FileUploadSubmission(extractionDate, successfullyUploadedFiles)

    "return 300 when passed valid request" in {
      given(migrationService.getUploadedBatch(refEq("batchId"))(any[HeaderCarrier]))
        .willReturn(Future.successful(successfullyUploadedFiles))
      given(migrationConnector.sendDataForProcessing(refEq(uploadSubmission))(any[HeaderCarrier]))
        .willReturn(Future.successful(HttpResponse.apply(ACCEPTED)))

      val result: Result =
        await(controller.initiateProcessing()(fakeInitiateRequest()))

      status(result) shouldBe SEE_OTHER

      verify(migrationService, atLeastOnce()).getUploadedBatch(refEq("batchId"))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce()).sendDataForProcessing(refEq(uploadSubmission))(any[HeaderCarrier])
    }

    "return 400 when passed an invalid request" in {
      val result: Result = await(controller.initiateProcessing()(newFakeGETRequestWithCSRF))

      status(result) shouldBe BAD_REQUEST

      verify(migrationService, never()).getUploadedBatch(any[String])(any[HeaderCarrier])
      verify(migrationConnector, never()).sendDataForProcessing(any[FileUploadSubmission])(any[HeaderCarrier])
    }

    "Handle 4xx Errors from service" in {
      given(migrationService.getUploadedBatch(refEq("batchId"))(any[HeaderCarrier]))
        .willReturn(Future.failed(Upstream4xxResponse("error", CONFLICT, 0)))

      intercept[Upstream4xxResponse] {
        await(controller.initiateProcessing()(fakeInitiateRequest()))
      }

      verify(migrationService, atLeastOnce()).getUploadedBatch(refEq("batchId"))(any[HeaderCarrier])
      verify(migrationConnector, never()).sendDataForProcessing(any[FileUploadSubmission])(any[HeaderCarrier])
    }

    "Handle 5xx Errors from connector" in {
      given(migrationService.getUploadedBatch(refEq("batchId"))(any[HeaderCarrier]))
        .willReturn(Future.successful(successfullyUploadedFiles))
      given(migrationConnector.sendDataForProcessing(refEq(uploadSubmission))(any[HeaderCarrier]))
        .willReturn(Future.failed(Upstream5xxResponse("error", INTERNAL_SERVER_ERROR, 0)))

      intercept[Upstream5xxResponse] {
        await(controller.initiateProcessing()(fakeInitiateRequest()))
      }

      verify(migrationService, atLeastOnce()).getUploadedBatch(refEq("batchId"))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce()).sendDataForProcessing(refEq(uploadSubmission))(any[HeaderCarrier])
    }

    "Handle unknown Errors from connector" in {
      given(migrationService.getUploadedBatch(refEq("batchId"))(any[HeaderCarrier]))
        .willReturn(Future.successful(successfullyUploadedFiles))
      given(migrationConnector.sendDataForProcessing(refEq(uploadSubmission))(any[HeaderCarrier]))
        .willReturn(Future.failed(new RuntimeException("error")))

      intercept[RuntimeException] {
        await(controller.initiateProcessing()(fakeInitiateRequest()))
      }

      verify(migrationService, atLeastOnce()).getUploadedBatch(refEq("batchId"))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce()).sendDataForProcessing(refEq(uploadSubmission))(any[HeaderCarrier])
    }
  }

  private def fakeInitiateRequest(
    batchId: String           = "batchId",
    extractionDate: LocalDate = extractionDate
  ): FakeRequest[AnyContentAsEmpty.type] = {
    implicit val format: Format[InitiateMigrationDataProcessing] = Json.format[InitiateMigrationDataProcessing]

    newFakeGETRequestWithCSRF
      .withJsonBody(Json.toJson(InitiateMigrationDataProcessing(batchId, extractionDate)))
      .asInstanceOf[FakeRequest[AnyContentAsEmpty.type]]
  }

  private def fakeMigrationDataUpload(
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
