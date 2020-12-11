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

import java.time.LocalDate

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileUploadSubmission, FileUploaded}
import uk.gov.hmrc.bindingtariffadminfrontend.model.transformation.HistoricTransformationStatistics
import uk.gov.hmrc.http.{BadRequestException, NotFoundException, Upstream5xxResponse}

class DataTransformationConnectorSpec extends ConnectorTest {

  private val connector = new DataTransformationConnector(mockAppConfig, authenticatedHttpClient, inject[WSClient])

  private val file = FileUploaded("name", "published", "text/plain", None, None)

  private val extractionDate = LocalDate.of(2020, 10, 10)

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
      val response = await(connector.sendDataForProcessing(FileUploadSubmission(extractionDate, List(file))))

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
        await(connector.sendDataForProcessing(FileUploadSubmission(extractionDate, List(file))))
      }

      verify(
        postRequestedFor(urlEqualTo("/binding-tariff-data-transformation/send-data-for-processing"))
      )
    }
  }

  "Connector getStatusOfJsonProcessing" should {

    "return the json for the multiple files" in {
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
      response.json   shouldBe Json.obj("status" -> "inserting")

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/processing-status"))
      )
    }

    "propagate errors" in {
      stubFor(
        get("/binding-tariff-data-transformation/processing-status")
          .willReturn(
            notFound()
              .withBody(Json.obj("status" -> "error").toString())
          )
      )

      intercept[NotFoundException] {
        await(connector.getStatusOfJsonProcessing)
      }

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/processing-status"))
      )
    }
  }

  "Connector downloadBTIJson" should {

    "return the json for the multiple files" in {
      val expected     = fromResource("filestore-initiate_response.json")
      val expectedJson = Json.prettyPrint(Json.parse(expected))

      stubFor(
        get("/binding-tariff-data-transformation/transformed-bti-records")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(expectedJson)
          )
      )
      val response = await(connector.downloadBTIJson)

      response.status                             shouldBe Status.OK
      Json.prettyPrint(Json.parse(response.body)) shouldBe expectedJson

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/transformed-bti-records"))
      )
    }
  }

  "Connector downloadLiabilitiesJson" should {

    "return the json for the multiple files" in {
      val expected     = fromResource("filestore-initiate_response.json")
      val expectedJson = Json.prettyPrint(Json.parse(expected))

      stubFor(
        get("/binding-tariff-data-transformation/transformed-liabilities-records")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(expected)
          )
      )

      val response = await(connector.downloadLiabilitiesJson)

      response.status                             shouldBe Status.OK
      Json.prettyPrint(Json.parse(response.body)) shouldBe expectedJson

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/transformed-liabilities-records"))
      )
    }
  }

  "Connector downloadMigrationReports" should {
    "return the json for the multiple files" in {
      val expected     = fromResource("filestore-initiate_response.json")
      val expectedJson = Json.prettyPrint(Json.parse(expected))

      stubFor(
        get("/binding-tariff-data-transformation/migration-reports")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(expected)
          )
      )

      val response = await(connector.downloadMigrationReports)

      response.status                             shouldBe Status.OK
      Json.prettyPrint(Json.parse(response.body)) shouldBe expectedJson

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/migration-reports"))
      )
    }
  }

  "Connector sendHistoricDataForProcessing" should {
    "return the json for the mutiple files" in {
      stubFor(
        post("/binding-tariff-data-transformation/send-historic-data-for-processing")
          .withHeader("Content-Type", equalTo("application/json"))
          .willReturn(
            aResponse()
              .withStatus(Status.ACCEPTED)
          )
      )
      val response = await(connector.sendHistoricDataForProcessing(List(file)))

      response.status shouldBe Status.ACCEPTED

      verify(
        postRequestedFor(urlEqualTo("/binding-tariff-data-transformation/send-historic-data-for-processing"))
      )
    }

    "propagate errors" in {
      stubFor(
        post("/binding-tariff-data-transformation/send-historic-data-for-processing")
          .willReturn(serverError())
      )

      intercept[Upstream5xxResponse] {
        await(connector.sendHistoricDataForProcessing(List(file)))
      }

      verify(
        postRequestedFor(urlEqualTo("/binding-tariff-data-transformation/send-historic-data-for-processing"))
      )
    }
  }

  "Connector getStatusOfHistoricDataProcessing" should {
    "return the json for the mutiple files" in {
      stubFor(
        get("/binding-tariff-data-transformation/historic-processing-status")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(Json.obj("status" -> "processing").toString())
          )
      )
      val response = await(connector.getStatusOfHistoricDataProcessing)

      response.status shouldBe Status.OK
      response.json   shouldBe Json.obj("status" -> "processing")

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/historic-processing-status"))
      )
    }

    "propagate errors" in {
      stubFor(
        get("/binding-tariff-data-transformation/historic-processing-status")
          .willReturn(
            notFound()
              .withBody(Json.obj("status" -> "error").toString())
          )
      )

      intercept[NotFoundException] {
        await(connector.getStatusOfHistoricDataProcessing)
      }

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/historic-processing-status"))
      )
    }
  }

  "Connector downloadHistoricJson" should {
    "return the json for the multiple files" in {
      val expected     = fromResource("filestore-initiate_response.json")
      val expectedJson = Json.prettyPrint(Json.parse(expected))

      stubFor(
        get("/binding-tariff-data-transformation/historic-data")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(expectedJson)
          )
      )
      val response = await(connector.downloadHistoricJson)

      response.status                             shouldBe Status.OK
      Json.prettyPrint(Json.parse(response.body)) shouldBe expectedJson

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/historic-data"))
      )
    }
  }

  "Connector deleteHistoricData" should {
    "DELETE everything the historic data" in {
      stubFor(
        delete("/binding-tariff-data-transformation/historic-data")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
          )
      )

      await(connector.deleteHistoricData())

      verify(
        deleteRequestedFor(urlEqualTo("/binding-tariff-data-transformation/historic-data"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

  "Connector getStatusOfHistoricDataTransformation" should {
    "return the json for the status" in {
      stubFor(
        get("/binding-tariff-data-transformation/historic-transformation-status")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(Json.obj("status" -> "transforming").toString())
          )
      )
      val response = await(connector.getStatusOfHistoricDataTransformation)

      response.status shouldBe Status.OK
      response.json   shouldBe Json.obj("status" -> "transforming")

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/historic-transformation-status"))
      )
    }

    "propagate errors" in {
      stubFor(
        get("/binding-tariff-data-transformation/historic-transformation-status")
          .willReturn(
            notFound()
              .withBody(Json.obj("status" -> "error").toString())
          )
      )

      intercept[NotFoundException] {
        await(connector.getStatusOfHistoricDataTransformation)
      }

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/historic-transformation-status"))
      )
    }
  }

  "Connector getHistoricTransformationStatistics" should {
    "return the json for the status" in {
      stubFor(
        get("/binding-tariff-data-transformation/historic-transformation-statistics")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(Json.obj("applCount" -> 1, "btiCount" -> 2).toString())
          )
      )
      val response = await(connector.getHistoricTransformationStatistics)

      response shouldBe HistoricTransformationStatistics(applCount = 1, btiCount = 2)

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/historic-transformation-statistics"))
      )
    }

    "propagate errors" in {
      stubFor(
        get("/binding-tariff-data-transformation/historic-transformation-statistics")
          .willReturn(badRequest())
      )

      intercept[BadRequestException] {
        await(connector.getHistoricTransformationStatistics)
      }

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/historic-transformation-statistics"))
      )
    }
  }

  "Connector initiateHistoricTransformation" should {
    "return accepted status" in {
      stubFor(
        post("/binding-tariff-data-transformation/initiate-historic-transformation")
          .willReturn(
            aResponse()
              .withStatus(Status.ACCEPTED)
          )
      )
      val response = await(connector.initiateHistoricTransformation)

      response.status shouldBe Status.ACCEPTED

      verify(
        postRequestedFor(urlEqualTo("/binding-tariff-data-transformation/initiate-historic-transformation"))
      )
    }

    "propagate errors" in {
      stubFor(
        post("/binding-tariff-data-transformation/initiate-historic-transformation")
          .willReturn(serverError())
      )

      intercept[Upstream5xxResponse] {
        await(connector.initiateHistoricTransformation)
      }

      verify(
        postRequestedFor(urlEqualTo("/binding-tariff-data-transformation/initiate-historic-transformation"))
      )
    }
  }

  "Connector downloadTransformedHistoricData" should {
    "return the json for the multiple files" in {
      val expected     = fromResource("filestore-initiate_response.json")
      val expectedJson = Json.prettyPrint(Json.parse(expected))

      stubFor(
        get("/binding-tariff-data-transformation/transformed-historic-data")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(expectedJson)
          )
      )
      val response = await(connector.downloadTransformedHistoricData)

      response.status                             shouldBe Status.OK
      Json.prettyPrint(Json.parse(response.body)) shouldBe expectedJson

      verify(
        getRequestedFor(urlEqualTo("/binding-tariff-data-transformation/transformed-historic-data"))
      )
    }
  }
}
