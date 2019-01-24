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

import java.time.Instant

import akka.actor.ActorSystem
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.BDDMockito.given
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.Environment
import play.api.http.Status
import play.api.libs.Files.TemporaryFile
import play.api.libs.ws.WSClient
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.FileUploaded
import uk.gov.hmrc.bindingtariffadminfrontend.model.{MigratableAttachment, MigrationFailedException}
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

  private val connector = new FileStoreConnector(config, wsClient, hmrcWsClient)

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

  "Connector Upload" should {
    "POST to the File Store" in {
      stubFor(
        post("/file")
          .willReturn(
            aResponse()
              .withStatus(Status.ACCEPTED)
              .withBody(fromResource("filestore-upload_response.json"))
          )
      )

      val url = TemporaryFile("example.txt").file.toURI.toURL.toString
      val file = MigratableAttachment(
        name = "file-name.txt",
        mimeType = "text/plain",
        url = url,
        timestamp = Instant.EPOCH
      )

      await(connector.upload(file)) shouldBe FileUploaded(
        id = "id",
        fileName = "file-name.txt",
        mimeType = "text/plain"
      )
      verify(
        postRequestedFor(urlEqualTo("/file"))
          .withAnyRequestBodyPart(aMultipart("file"))
      )
    }

    "Handle FileNotFound" in {
      val file = MigratableAttachment(
        name = "file-name.txt",
        mimeType = "text/plain",
        url = "http://google.com/image.png",
        timestamp = Instant.EPOCH
      )

      intercept[MigrationFailedException] {
        await(connector.upload(file)) shouldBe FileUploaded(
          id = "id",
          fileName = "file-name.txt",
          mimeType = "text/plain"
        )
      }.getMessage shouldBe "File didnt exist at [http://google.com/image.png]"
    }

    "Handle Malformed URL" in {
      val file = MigratableAttachment(
        name = "file-name.txt",
        mimeType = "text/plain",
        url = "some url",
        timestamp = Instant.EPOCH
      )

      intercept[MigrationFailedException] {
        await(connector.upload(file)) shouldBe FileUploaded(
          id = "id",
          fileName = "file-name.txt",
          mimeType = "text/plain"
        )
      }.getMessage shouldBe "File had invalid URL [some url]"
    }

    "Handle Error" in {
      val file = MigratableAttachment(
        name = "file-name.txt",
        mimeType = "text/plain",
        url = "http://localhost:123/image.png",
        timestamp = Instant.EPOCH
      )

      intercept[MigrationFailedException] {
        await(connector.upload(file)) shouldBe FileUploaded(
          id = "id",
          fileName = "file-name.txt",
          mimeType = "text/plain"
        )
      }.getMessage shouldBe "File was inaccessible [http://localhost:123/image.png] due to [Connection refused (Connection refused)]"
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
