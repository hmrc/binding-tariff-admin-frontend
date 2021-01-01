/*
 * Copyright 2021 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito.given
import org.mockito.Mockito.reset
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status._
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{MultipartFormData, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtariffadminfrontend.forms.UploadAttachmentFormProvider
import uk.gov.hmrc.bindingtariffadminfrontend.model.AttachmentUpload
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}

import scala.concurrent.Future

class FileMigrationUploadControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val migrationService = mock[DataMigrationService]

  private val controller = new FileMigrationUploadController(
    authenticatedAction          = new SuccessfulAuthenticatedAction,
    service                      = migrationService,
    uploadAttachmentFormProvider = new UploadAttachmentFormProvider,
    mcc                          = mcc,
    messagesApi                  = messageApi,
    appConfig                    = realConfig
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(migrationService)
  }

  "GET /" should {
    "return 200" in {
      val result: Result = await(controller.get()(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("file_migration_upload-heading")
    }
  }

  "POST /" should {
    "Upload" in {
      given(migrationService.upload(any[AttachmentUpload], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .successful(())

      val result: Result = await(controller.post(fakeAttachmentUpload()))

      status(result) shouldBe ACCEPTED
    }

    "Handle 4xx Errors" in {
      given(migrationService.upload(any[AttachmentUpload], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .failed(Upstream4xxResponse("error", CONFLICT, 0))

      val result: Result = await(controller.post(fakeAttachmentUpload()))

      status(result) shouldBe CONFLICT
    }

    "Handle 5xx Errors" in {
      given(migrationService.upload(any[AttachmentUpload], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .failed(Upstream5xxResponse("error", INTERNAL_SERVER_ERROR, 0))

      val result: Result = await(controller.post(fakeAttachmentUpload()))

      status(result) shouldBe BAD_GATEWAY
    }

    "Handle unknown Errors" in {
      given(migrationService.upload(any[AttachmentUpload], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future
        .failed(new RuntimeException("error"))

      val result: Result = await(controller.post(fakeAttachmentUpload()))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  private def fakeAttachmentUpload(
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
