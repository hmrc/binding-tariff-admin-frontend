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
import com.github.tomakehurst.wiremock.matching.{MultipartValuePattern, MultipartValuePatternBuilder}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.Environment
import play.api.http.Status
import play.api.libs.Files.TemporaryFile
import play.api.libs.ws.WSClient
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.UploadTemplate
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class UpscanS3ConnectorTest extends UnitSpec with WithFakeApplication with WiremockTestServer
  with MockitoSugar with ResourceFiles {

  private val config = mock[AppConfig]

  private val actorSystem = ActorSystem.create("test")
  private val wsClient: WSClient = fakeApplication.injector.instanceOf[WSClient]
  private val auditConnector = new DefaultAuditConnector(fakeApplication.configuration, fakeApplication.injector.instanceOf[Environment])
  private val hmrcWsClient = new DefaultHttpClient(fakeApplication.configuration, auditConnector, wsClient, actorSystem)
  private implicit val multipartBuilder: MultipartValuePatternBuilder => MultipartValuePattern = _.build()
  private implicit val headers: HeaderCarrier = HeaderCarrier()

  private val connector = new UpscanS3Connector(config, hmrcWsClient)

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

      await(connector.upload(templateUploading, TemporaryFile("example-file.json")))

      verify(
        postRequestedFor(urlEqualTo("/path"))
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

      intercept[RuntimeException] {
        await(connector.upload(templateUploading, TemporaryFile("example-file.json")))
      }.getMessage shouldBe "Bad AWS response with status [502] body [content]"
    }
  }

}
