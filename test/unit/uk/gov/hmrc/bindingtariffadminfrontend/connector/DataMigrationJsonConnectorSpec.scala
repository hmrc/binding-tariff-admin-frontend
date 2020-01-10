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
import org.apache.http.HttpStatus
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.FileUploaded
import uk.gov.hmrc.http.{HttpResponse, Upstream5xxResponse}

class DataMigrationJsonConnectorSpec extends ConnectorTest {

  private val connector = new DataMigrationJsonConnector(appConfig, authenticatedHttpClient)

  private val file = FileUploaded("name", "published", "text/plain", None, None)

  "Connector generateJson" should {
    "return the json for the mutiple files" in {
      stubFor(
        post("/spike-binding-tariff-data-migration/json")
          .withHeader("Content-Type", equalTo("application/json"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromResource("filestore-initiate_response.json"))
          )
      )
      val response = await(connector.generateJson(List(file)))

      response shouldBe Json.parse("""{
                                     |  "href": "url",
                                     |  "fields": {
                                     |    "field": "value"
                                     |  }
                                     |}""".stripMargin)

      verify(
        postRequestedFor(urlEqualTo("/spike-binding-tariff-data-migration/json"))
      )
    }

    "propagate errors" in {
      stubFor(
        post("/spike-binding-tariff-data-migration/json")
        .willReturn(serverError())
      )

      intercept[Upstream5xxResponse] {
        await(connector.generateJson(List(file)))
      }

      verify(
        postRequestedFor(urlEqualTo("/spike-binding-tariff-data-migration/json"))
      )
    }
  }
}
