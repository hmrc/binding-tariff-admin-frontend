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

package uk.gov.hmrc.bindingtariffadminfrontend.connector

import akka.actor.ActorSystem
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.BDDMockito.given
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.Environment
import play.api.http.Status
import play.api.libs.ws.WSClient
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileUploaded, UploadRequest, UploadTemplate}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class FileStoreConnectorTest extends UnitSpec with WithFakeApplication with WiremockTestServer with MockitoSugar with BeforeAndAfterEach with ResourceFiles {

  private val config = mock[AppConfig]
  private val wsClient: WSClient = fakeApplication.injector.instanceOf[WSClient]
  private val auditConnector = new DefaultAuditConnector(fakeApplication.configuration, fakeApplication.injector.instanceOf[Environment])
  private val actorSystem = ActorSystem.create("test")
  private val hmrcWsClient = new DefaultHttpClient(fakeApplication.configuration, auditConnector, wsClient, actorSystem)
  private implicit val headers: HeaderCarrier = HeaderCarrier()

  private val connector = new FileStoreConnector(config, hmrcWsClient)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(config.filestoreUrl).willReturn(wireMockUrl)
  }

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

      verify(deleteRequestedFor(urlEqualTo("/file/id")))
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

      verify(deleteRequestedFor(urlEqualTo("/file")))
    }
  }

  "Connector Initiate" should {
    "POST to the File Store" in {
      stubFor(
        post("/file")
          .withHeader("Content-Type", equalTo("application/json"))
          .withRequestBody(matchingJsonPath("id", equalTo("file_name_jpg")))
          .withRequestBody(matchingJsonPath("fileName", equalTo("file name.jpg")))
          .withRequestBody(matchingJsonPath("mimeType", equalTo("type")))
          .willReturn(
            aResponse()
              .withStatus(Status.ACCEPTED)
              .withBody(fromResource("filestore-initiate_response.json"))
          )
      )

      val file = UploadRequest(fileName = "file name.jpg", mimeType = "type")
      val response = await(connector.initiate(file))

      response shouldBe UploadTemplate("url", Map("field" -> "value"))
    }
  }

  "Connector Get" should {

    "GET from the File Store" in {
      stubFor(
        get("/file/id")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromResource("filestore-publish_response.json"))
          )
      )

      await(connector.get("id")) shouldBe FileUploaded(
        id = "id",
        fileName = "file-name.txt",
        mimeType = "text/plain",
        published = true
      )
    }
  }

  "Connector Get Many" should {

    "GET from the File Store" in {
      stubFor(
        get("/file?id=id")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromResource("filestore-search_response.json"))
          )
      )

      await(connector.get(Seq("id"))) shouldBe Seq(FileUploaded(
        id = "id",
        fileName = "file-name.txt",
        mimeType = "text/plain",
        published = true
      ))
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
        id = "id",
        fileName = "file-name.txt",
        mimeType = "text/plain",
        published = true
      )
    }
  }

}
