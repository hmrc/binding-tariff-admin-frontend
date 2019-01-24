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
import org.apache.http.HttpStatus
import org.mockito.BDDMockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Environment
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.Cases
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}


class BindingTariffClassificationConnectorSpec extends UnitSpec
  with WiremockTestServer with MockitoSugar with WithFakeApplication {

  private val configuration = mock[AppConfig]
  private val actorSystem = ActorSystem.create("test")
  private val wsClient: WSClient = fakeApplication.injector.instanceOf[WSClient]
  private val auditConnector = new DefaultAuditConnector(fakeApplication.configuration, fakeApplication.injector.instanceOf[Environment])
  private val client = new DefaultHttpClient(fakeApplication.configuration, auditConnector, wsClient, actorSystem)
  private implicit val hc = HeaderCarrier()

  private val connector = new BindingTariffClassificationConnector(configuration, client)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(configuration.classificationBackendUrl).willReturn(wireMockUrl)
  }

  "Connector 'Create Case'" should {
    val request = Cases.btiCaseExample
    val requestJSON = Json.toJson(request).toString()

    "Create valid case" in {
      val response = Cases.btiCaseExample
      val responseJSON = Json.toJson(response).toString()

      stubFor(put(urlEqualTo(s"/cases/${request.reference}"))
        .withRequestBody(equalToJson(requestJSON))
        .willReturn(aResponse()
          .withStatus(HttpStatus.SC_OK)
          .withBody(responseJSON)
        )
      )

      await(connector.upsertCase(request)) shouldBe response
    }

    "propagate errors" in {
      stubFor(put(urlEqualTo(s"/cases/${request.reference}"))
        .willReturn(aResponse()
          .withStatus(HttpStatus.SC_BAD_GATEWAY)
        )
      )

      intercept[Upstream5xxResponse] {
        await(connector.upsertCase(request))
      }
    }
  }

  "Connector 'GET Case'" should {
    val ref = Cases.btiCaseExample.reference

    "Get valid case" in {
      val response = Cases.btiCaseExample
      val responseJSON = Json.toJson(response).toString()

      stubFor(get(urlEqualTo(s"/cases/$ref"))
        .willReturn(aResponse()
          .withStatus(HttpStatus.SC_OK)
          .withBody(responseJSON)
        )
      )

      await(connector.getCase(ref)) shouldBe Some(response)
    }

    "Return None for 404" in {
      stubFor(get(urlEqualTo(s"/cases/$ref"))
        .willReturn(aResponse()
          .withStatus(HttpStatus.SC_NOT_FOUND)
        )
      )

      await(connector.getCase(ref)) shouldBe None
    }
  }

  "Connector Delete All Cases" should {
    "DELETE from the Case Store" in {
      stubFor(
        delete("/cases")
          .willReturn(
            aResponse()
              .withStatus(Status.NO_CONTENT)
          )
      )

      await(connector.deleteCases)

      verify(deleteRequestedFor(urlEqualTo("/cases")))
    }
  }

  "Connector Delete All Events" should {
    "DELETE from the Case Store" in {
      stubFor(
        delete("/events")
          .willReturn(
            aResponse()
              .withStatus(Status.NO_CONTENT)
          )
      )

      await(connector.deleteEvents)

      verify(deleteRequestedFor(urlEqualTo("/events")))
    }
  }

}
