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

package uk.gov.hmrc.bindingtariffadminfrontend.connector

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.{MultipartValuePattern, MultipartValuePatternBuilder}
import play.api.http.Status
import play.api.libs.Files.SingletonTemporaryFileCreator
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{AttachmentUpload, UploadTemplate}

class UpscanS3ConnectorTest extends ConnectorTest {

  private implicit val multipartBuilder: MultipartValuePatternBuilder => MultipartValuePattern = _.build()

  private val connector = new UpscanS3Connector()

  "Upload" should {

    "POST to AWS" in {
      stubFor(
        post("/path")
          .willReturn(
            aResponse()
              .withStatus(Status.NO_CONTENT)
          )
      )

      val templateUploading = UploadTemplate(
        href = s"$wireMockUrl/path",
        fields = Map(
          "key" -> "value"
        )
      )

      val file = SingletonTemporaryFileCreator.create("example-file.json")

      val upload = UploadAttachmentRequest("example-file.json", "application/json")

      await(connector.upload(templateUploading, file, upload)) shouldBe ((): Unit)

      verify(
        postRequestedFor(urlEqualTo("/path"))
          .withoutHeader("X-Api-Token")
          .withRequestBodyPart(aMultipart("file"))
          .withRequestBodyPart(aMultipart("key").withBody(equalTo("value")))
      )
    }

    "Handle Bad Responses" in {
      stubFor(
        post("/path")
          .willReturn(
            aResponse()
              .withStatus(Status.BAD_GATEWAY)
              .withBody("content")
          )
      )

      val templateUploading = UploadTemplate(
        href = s"$wireMockUrl/path",
        fields = Map(
          "key" -> "value"
        )
      )

      val file = SingletonTemporaryFileCreator.create("example-file.json")

      val upload = UploadAttachmentRequest("example-file.json", "application/json")

      intercept[RuntimeException] {
        await(connector.upload(templateUploading, file, upload))
      }.getMessage shouldBe "Bad AWS response with status [502] body [content]"

      verify(
        postRequestedFor(urlEqualTo("/path"))
          .withoutHeader("X-Api-Token")
      )
    }

  }

}
