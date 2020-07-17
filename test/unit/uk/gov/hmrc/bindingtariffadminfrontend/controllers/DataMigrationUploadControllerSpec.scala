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

import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito.given
import play.api.http.Status.OK
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{MultipartFormData, Result}
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{UploadMigrationDataRequest, UploadRequest}
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}

import scala.concurrent.Future

class DataMigrationUploadControllerSpec extends ControllerSpec {

  private val migrationService = mock[DataMigrationService]
  private val controller = new DataMigrationUploadController(
    new SuccessfulAuthenticatedAction, migrationService, mcc, messageApi, realConfig
  )

  "GET /" should {
    "return 200" in {
      val result: Result = await(controller.get()(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("data_migration_upload-heading")
    }
  }

  "POST /" should {
    val request = UploadMigrationDataRequest(
      fileName = "filename",
      mimeType = "text/csv"
    )

    "Upload" in {
      given(migrationService.upload(any[UploadRequest], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future.successful(())

      val f = aForm(filename = "filename", mimeType = "text/csv")
      val result: Result = await(controller.post(newFakePOSTRequestWithCSRF.withBody(f)))

      status(result) shouldBe 202
    }

    "Handle 4xx Errors" in {
      given(migrationService.upload(any[UploadRequest], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future.failed(Upstream4xxResponse("error", 409, 0))

      val result: Result = await(controller.post(newFakePOSTRequestWithCSRF.withBody(aForm())))

      status(result) shouldBe 409
    }

    "Handle 5xx Errors" in {
      given(migrationService.upload(any[UploadRequest], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future.failed(Upstream5xxResponse("error", 500, 0))

      val result: Result = await(controller.post(newFakePOSTRequestWithCSRF.withBody(aForm())))

      status(result) shouldBe 502
    }

    "Handle unknown Errors" in {
      given(migrationService.upload(any[UploadRequest], any[TemporaryFile])(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("error"))

      val result: Result = await(controller.post(newFakePOSTRequestWithCSRF.withBody(aForm())))

      status(result) shouldBe 500
    }

    def aForm(filename: String = "file.txt", mimeType: String = "text/html"): MultipartFormData[TemporaryFile] = {
      val file = SingletonTemporaryFileCreator.create(filename)
      val filePart = FilePart[TemporaryFile](key = "file", filename, contentType = Some(mimeType), ref = file)
      MultipartFormData[TemporaryFile](
        dataParts = Map("id" -> Seq(filename), "filename" -> Seq(filename), "mimetype" -> Seq(mimeType)),
        files = Seq(filePart),
        badParts = Seq.empty
      )
    }

  }
}
