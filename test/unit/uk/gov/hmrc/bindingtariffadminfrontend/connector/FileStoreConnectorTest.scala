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

package uk.gov.hmrc.bindingtariffadminfrontend.connector

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileSearch, FileUploaded, UploadTemplate}
import uk.gov.hmrc.bindingtariffadminfrontend.model.{AttachmentUpload, Pagination}

class FileStoreConnectorTest extends ConnectorTest {

  private val connector = new FileStoreConnector(mockAppConfig, authenticatedHttpClient)

  "Connector Delete" should {
    "DELETE from the File Store" in {
      stubFor(
        delete("/file/id")
          .willReturn(
            aResponse()
              .withStatus(Status.NO_CONTENT)
          )
      )

      await(connector.delete("id"))

      verify(
        deleteRequestedFor(urlEqualTo("/file/id"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

  "Connector Delete All" should {
    "DELETE from the File Store" in {
      stubFor(
        delete("/file")
          .willReturn(
            aResponse()
              .withStatus(Status.NO_CONTENT)
          )
      )

      await(connector.delete)

      verify(
        deleteRequestedFor(urlEqualTo("/file"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

  "Connector Initiate" should {
    "POST to the File Store" in {
      stubFor(
        post("/file")
          .withHeader("Content-Type", equalTo("application/json"))
          .withRequestBody(matchingJsonPath("id", equalTo("id")))
          .withRequestBody(matchingJsonPath("fileName", equalTo("file name.jpg")))
          .withRequestBody(matchingJsonPath("mimeType", equalTo("type")))
          .willReturn(
            aResponse()
              .withStatus(Status.ACCEPTED)
              .withBody(fromResource("filestore-initiate_response.json"))
          )
      )

      val file = AttachmentUpload(
        fileName = "file name.jpg",
        mimeType = "type",
        id       = "id",
        batchId  = UUID.randomUUID().toString
      )

      val response = await(connector.initiate(file))

      response shouldBe UploadTemplate("url", Map("field" -> "value"))

      verify(
        postRequestedFor(urlEqualTo("/file"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

  "Connector Find" should {

    "GET from the File Store" in {
      stubFor(
        get("/file/id")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromResource("filestore-publish_response.json"))
          )
      )

      await(connector.find("id")) shouldBe Some(
        FileUploaded(
          id        = "id",
          fileName  = "file-name.txt",
          mimeType  = "text/plain",
          published = true
        )
      )

      verify(
        getRequestedFor(urlEqualTo("/file/id"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

  "Connector Find Many" should {

    "GET from the File Store" in {
      stubFor(
        get("/file?id=id&page=1&page_size=2")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromResource("filestore-search_response.json"))
          )
      )

      await(connector.find(FileSearch(ids = Some(Set("id"))), Pagination(1, 2))).results shouldBe Seq(
        FileUploaded(
          id        = "id",
          fileName  = "file-name.txt",
          mimeType  = "text/plain",
          published = true
        )
      )

      verify(
        getRequestedFor(urlEqualTo("/file?id=id&page=1&page_size=2"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }

    "use multiple requests to the File Store" in {
      val batchSize  = 48
      val numBatches = 5
      val ids        = (1 to batchSize * numBatches).map(_ => UUID.randomUUID().toString).toSet

      stubFor(
        get(urlMatching(s"/file\\?(&?id=[a-f0-9-]+)+&page=1&page_size=2147483647"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromResource("filestore-search_response.json"))
          )
      )

      await(
        connector.find(FileSearch(ids = Some(ids)), Pagination.max).results shouldBe (1 to numBatches).map(_ =>
          FileUploaded(
            id        = "id",
            fileName  = "file-name.txt",
            mimeType  = "text/plain",
            published = true
          )
        )
      )
    }
  }

  "Connector Publish" should {

    "POST to the File Store" in {
      stubFor(
        post("/file/id/publish")
          .willReturn(
            aResponse()
              .withStatus(Status.ACCEPTED)
              .withBody(fromResource("filestore-publish_response.json"))
          )
      )

      await(connector.publish("id")) shouldBe FileUploaded(
        id        = "id",
        fileName  = "file-name.txt",
        mimeType  = "text/plain",
        published = true
      )

      verify(
        postRequestedFor(urlEqualTo("/file/id/publish"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

}
