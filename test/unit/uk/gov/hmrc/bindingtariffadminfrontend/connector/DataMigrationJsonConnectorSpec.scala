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
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.FileUploaded
import uk.gov.hmrc.http.{NotFoundException, Upstream5xxResponse}

class DataMigrationJsonConnectorSpec extends ConnectorTest {

  private val connector = new DataMigrationJsonConnector(appConfig, authenticatedHttpClient, inject[WSClient])

  private val file = FileUploaded("name", "published", "text/plain", None, None)

  "Connector sendDataForProcessing" should {

    "return the json for the mutiple files" in {
      stubFor(
        post("/binding-tariff-data-transformation/send-data-for-processing")
          .withHeader("Content-Type", equalTo("application/json"))
          .willReturn(
            aResponse()
              .withStatus(Status.ACCEPTED)
          )
      )
      val response = await(connector.sendDataForProcessing(List(file)))

      response.status shouldBe Status.ACCEPTED

      verify(
        postRequestedFor(urlEqualTo("/binding-tariff-data-transformation/send-data-for-processing"))
      )
    }

    "propagate errors" in {
      stubFor(
        post("/binding-tariff-data-transformation/send-data-for-processing")
        .willReturn(serverError())
      )

      intercept[Upstream5xxResponse] {
        await(connector.sendDataForProcessing(List(file)))
      }

      verify(
        postRequestedFor(urlEqualTo("/binding-tariff-data-transformation/send-data-for-processing"))
      )
    }
  }

  "Connector getStatusOfJsonProcessing" should {

    "return the json for the mutiple files" in {
      stubFor(
        get("/binding-tariff-data-transformation/processing-status")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(Json.obj("status" -> "inserting").toString())
          )
      )
      val response = await(connector.getStatusOfJsonProcessing)

      response.status shouldBe Status.OK
      response.json shouldBe Json.obj("status" -> "inserting")

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/processing-status"))
      )
    }

    "propagate errors" in {
      stubFor(
        get("/binding-tariff-data-transformation/processing-status")
          .willReturn(notFound()
          .withBody(Json.obj("status" -> "error").toString()))
      )

      intercept[NotFoundException] {
        await(connector.getStatusOfJsonProcessing)
      }

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/processing-status"))
      )
    }
  }

  "Connector downloadJson" should {

    "return the json for the mutiple files" in {
      stubFor(
        get("/binding-tariff-data-transformation/tranformed-bti-records")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromResource("filestore-initiate_response.json"))
          )
      )
      val response = await(connector.downloadJson)

      response.headers.status shouldBe Status.OK
      response.body.map{ res =>
        res shouldBe """{
                   |  "href": "url",
                   |  "fields": {
                   |    "field": "value"
                   |  }
                   |}""".stripMargin
        }

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/tranformed-bti-records"))
      )
    }

    "propagate errors" in {
      stubFor(
        get("/binding-tariff-data-transformation/tranformed-bti-records")
          .willReturn(notFound()
            .withBody(Json.obj("status" -> "error").toString()))
      )

      intercept[NotFoundException] {
        await(connector.getStatusOfJsonProcessing)
      }

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/tranformed-bti-records"))
      )
    }
  }
}
