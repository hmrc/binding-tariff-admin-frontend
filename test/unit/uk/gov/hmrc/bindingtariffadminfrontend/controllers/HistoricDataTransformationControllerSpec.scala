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

package uk.gov.hmrc.bindingtariffadminfrontend.controllers

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.mvc.Result
import uk.gov.hmrc.bindingtariffadminfrontend.connector.DataTransformationConnector
import uk.gov.hmrc.bindingtariffadminfrontend.model.transformation.HistoricTransformationStatistics
import uk.gov.hmrc.http._

import scala.concurrent.Future

class HistoricDataTransformationControllerSpec extends ControllerSpec with BeforeAndAfterEach {
  private val migrationConnector = mock[DataTransformationConnector]

  private val controller = new HistoricDataTransformationController(
    authenticatedAction = new SuccessfulAuthenticatedAction,
    connector           = migrationConnector,
    mcc                 = mcc,
    messagesApi         = messageApi,
    appConfig           = realConfig
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(migrationConnector)
  }

  "GET /" should {
    "return 200" in {
      given(migrationConnector.getHistoricTransformationStatistics(any[HeaderCarrier]))
        .willReturn(
          Future.successful(HistoricTransformationStatistics(applCount = 1, btiCount = 2))
        )

      val result: Result = await(controller.get()(newFakeGETRequestWithCSRF))

      status(result) shouldBe OK
      bodyOf(result) should include("historic_data_migration_transform-heading")
    }
  }

  "status /" should {
    "return 200" in {
      val result: Result = await(controller.status()(newFakeRequestWithCSRF))
      status(result) shouldBe OK
    }
  }

  "getStatusOfHistoricDataTransformation /" should {
    "return 200" in {
      given(migrationConnector.getStatusOfHistoricDataTransformation(any[HeaderCarrier]))
        .willReturn(
          Future.successful(HttpResponse.apply(OK, responseJson = Some(Json.obj("status" -> "transforming"))))
        )

      val result: Result = await(controller.getStatusOfHistoricDataTransformation()(newFakeRequestWithCSRF))

      status(result)     shouldBe OK
      jsonBodyOf(result) shouldBe Json.obj("status" -> "transforming")
    }

    "return 400" in {
      given(migrationConnector.getStatusOfHistoricDataTransformation(any[HeaderCarrier]))
        .willReturn(
          Future.successful(
            HttpResponse.apply(BAD_REQUEST, responseJson = Some(Json.obj("error" -> "error while transforming")))
          )
        )

      val result: Result = await(controller.getStatusOfHistoricDataTransformation()(newFakeRequestWithCSRF))

      status(result)     shouldBe BAD_REQUEST
      jsonBodyOf(result) shouldBe Json.obj("error" -> "error while transforming")
    }
  }

  "downloadTransformedJson /" should {
    "return 200" in {
      val source               = Source.single(ByteString.fromString("~~archive~~"))
      val response: WSResponse = mock[WSResponse]
      when(response.status).thenReturn(OK)
      when(response.bodyAsSource: Source[ByteString, Any]).thenReturn(source)

      given(migrationConnector.downloadTransformedHistoricData).willReturn(Future.successful(response))

      val result = await(controller.downloadTransformedJson()(newFakeRequestWithCSRF))

      status(result) shouldBe OK
      bodyOf(result) shouldBe "~~archive~~"
    }

    "return 400" in {
      val response: WSResponse = mock[WSResponse]
      when(response.status).thenReturn(BAD_REQUEST)

      given(migrationConnector.downloadTransformedHistoricData).willReturn(Future.successful(response))

      intercept[BadRequestException](
        await(controller.downloadTransformedJson()(newFakeRequestWithCSRF))
      )
    }
  }

  "initiateProcessing /" should {

    "return 300 when passed valid request" in {
      given(migrationConnector.initiateHistoricTransformation(any[HeaderCarrier]))
        .willReturn(Future.successful(HttpResponse.apply(ACCEPTED)))

      val result: Result = await(controller.initiate(newFakeRequestWithCSRF))

      status(result) shouldBe SEE_OTHER

      verify(migrationConnector, atLeastOnce()).initiateHistoricTransformation(any[HeaderCarrier])
    }

    "Handle 5xx Errors from connector" in {
      given(migrationConnector.initiateHistoricTransformation(any[HeaderCarrier]))
        .willReturn(Future.failed(Upstream5xxResponse("error", INTERNAL_SERVER_ERROR, 0)))

      intercept[Upstream5xxResponse] {
        await(controller.initiate(newFakeRequestWithCSRF))
      }

      verify(migrationConnector, atLeastOnce())
        .initiateHistoricTransformation(any[HeaderCarrier])
    }

    "Handle unknown Errors from connector" in {
      given(migrationConnector.initiateHistoricTransformation(any[HeaderCarrier]))
        .willReturn(Future.failed(new RuntimeException("error")))

      intercept[RuntimeException] {
        await(controller.initiate(newFakeRequestWithCSRF))
      }

      verify(migrationConnector, atLeastOnce())
        .initiateHistoricTransformation(any[HeaderCarrier])
    }
  }
}
