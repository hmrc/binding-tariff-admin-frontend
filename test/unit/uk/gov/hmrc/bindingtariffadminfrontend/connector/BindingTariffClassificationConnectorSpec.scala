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

import java.time.Instant

import com.github.tomakehurst.wiremock.client.WireMock._
import org.apache.http.HttpStatus
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification._
import uk.gov.hmrc.bindingtariffadminfrontend.model.{Cases, Paged, Pagination}
import uk.gov.hmrc.http.{NotFoundException, Upstream5xxResponse}

class BindingTariffClassificationConnectorSpec extends ConnectorTest {

  private val connector = new BindingTariffClassificationConnector(mockAppConfig, authenticatedHttpClient)

  "Connector 'Create Case'" should {
    val request     = Cases.btiCaseExample
    val requestJSON = Json.toJson(request).toString()

    "Create valid case" in {
      val response     = Cases.btiCaseExample
      val responseJSON = Json.toJson(response).toString()

      stubFor(
        put(urlEqualTo(s"/cases/${request.reference}"))
          .withRequestBody(equalToJson(requestJSON))
          .willReturn(
            aResponse()
              .withStatus(HttpStatus.SC_OK)
              .withBody(responseJSON)
          )
      )

      await(connector.upsertCase(request)) shouldBe response

      verify(
        putRequestedFor(urlEqualTo(s"/cases/${request.reference}"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }

    "propagate errors" in {
      stubFor(
        put(urlEqualTo(s"/cases/${request.reference}"))
          .willReturn(
            aResponse()
              .withStatus(HttpStatus.SC_BAD_GATEWAY)
          )
      )

      intercept[Upstream5xxResponse] {
        await(connector.upsertCase(request))
      }

      verify(
        putRequestedFor(urlEqualTo(s"/cases/${request.reference}"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

  "Connector 'Create Event'" should {
    val event       = Event(Note("note"), Operator("id"), "ref", Instant.now())
    val requestJSON = Json.toJson(event).toString()

    "Create valid event" in {
      val responseJSON = Json.toJson(event).toString()

      stubFor(
        post(urlEqualTo("/cases/ref/events"))
          .withRequestBody(equalToJson(requestJSON))
          .willReturn(
            aResponse()
              .withStatus(HttpStatus.SC_OK)
              .withBody(responseJSON)
          )
      )

      await(connector.createEvent("ref", event)) shouldBe event

      verify(
        postRequestedFor(urlEqualTo("/cases/ref/events"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }

    "propagate errors" in {
      stubFor(
        post(urlEqualTo("/cases/ref/events"))
          .willReturn(
            aResponse()
              .withStatus(HttpStatus.SC_BAD_GATEWAY)
          )
      )

      intercept[Upstream5xxResponse] {
        await(connector.createEvent("ref", event))
      }

      verify(
        postRequestedFor(urlEqualTo("/cases/ref/events"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

  "Connector 'GET Events'" should {
    val event = Event(Note("note"), Operator("id"), "ref", Instant.now())

    "Get valid events" in {
      val responseJSON = Json.toJson(Paged(Seq(event))).toString()

      stubFor(
        get(urlEqualTo("/cases/ref/events"))
          .willReturn(
            aResponse()
              .withStatus(HttpStatus.SC_OK)
              .withBody(responseJSON)
          )
      )

      await(connector.getEvents("ref", Pagination())) shouldBe Paged(Seq(event))

      verify(
        getRequestedFor(urlEqualTo("/cases/ref/events"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }

    "Return failed for 404" in {
      stubFor(
        get(urlEqualTo("/cases/ref/events"))
          .willReturn(
            aResponse()
              .withStatus(HttpStatus.SC_NOT_FOUND)
          )
      )

      intercept[NotFoundException] {
        await(connector.getEvents("ref", Pagination()))
      }

      verify(
        getRequestedFor(urlEqualTo("/cases/ref/events"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

  "Connector 'Search Events'" should {
    val event = Event(Note("note"), Operator("id"), "ref", Instant.now())

    "Get valid events" in {
      val responseJSON = Json.toJson(Paged(Seq(event))).toString()

      stubFor(
        get(urlEqualTo("/events?page=1&page_size=2"))
          .willReturn(
            aResponse()
              .withStatus(HttpStatus.SC_OK)
              .withBody(responseJSON)
          )
      )

      await(connector.getEvents(EventSearch(), Pagination(1, 2))) shouldBe Paged(Seq(event))

      verify(
        getRequestedFor(urlEqualTo("/events?page=1&page_size=2"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }

    "Return failed for 404" in {
      stubFor(
        get(urlEqualTo("/events?page=1&page_size=2"))
          .willReturn(
            aResponse()
              .withStatus(HttpStatus.SC_NOT_FOUND)
          )
      )

      intercept[NotFoundException] {
        await(connector.getEvents(EventSearch(), Pagination(1, 2)))
      }

      verify(
        getRequestedFor(urlEqualTo("/events?page=1&page_size=2"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

  "Connector 'GET Case'" should {
    val ref = Cases.btiCaseExample.reference

    "Get valid case" in {
      val response     = Cases.btiCaseExample
      val responseJSON = Json.toJson(response).toString()

      stubFor(
        get(urlEqualTo(s"/cases/$ref"))
          .willReturn(
            aResponse()
              .withStatus(HttpStatus.SC_OK)
              .withBody(responseJSON)
          )
      )

      await(connector.getCase(ref)) shouldBe Some(response)

      verify(
        getRequestedFor(urlEqualTo(s"/cases/$ref"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }

    "Return None for 404" in {
      stubFor(
        get(urlEqualTo(s"/cases/$ref"))
          .willReturn(
            aResponse()
              .withStatus(HttpStatus.SC_NOT_FOUND)
          )
      )

      await(connector.getCase(ref)) shouldBe None

      verify(
        getRequestedFor(urlEqualTo(s"/cases/$ref"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

  "Connector 'GET Cases'" should {

    "Get valid case" in {
      val response     = Paged(Seq(Cases.btiCaseExample))
      val responseJSON = Json.toJson(response).toString()

      stubFor(
        get(urlEqualTo(s"/cases?page=1&page_size=2"))
          .willReturn(
            aResponse()
              .withStatus(HttpStatus.SC_OK)
              .withBody(responseJSON)
          )
      )

      await(connector.getCases(CaseSearch(), Pagination(1, 2))) shouldBe response

      verify(
        getRequestedFor(urlEqualTo(s"/cases?page=1&page_size=2"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }

    "Get valid case with filters" in {
      val search = CaseSearch(sortDirection = Some(SortDirection.DESCENDING), sortField = Some(SortField.CREATED_DATE))
      val filter = CaseSearch.bindable.unbind("", search)

      val response     = Paged(Seq(Cases.btiCaseExample))
      val responseJSON = Json.toJson(response).toString()

      stubFor(
        get(urlEqualTo(s"/cases?page=1&page_size=2&$filter"))
          .willReturn(
            aResponse()
              .withStatus(HttpStatus.SC_OK)
              .withBody(responseJSON)
          )
      )

      await(connector.getCases(search, Pagination(1, 2))) shouldBe response

      verify(
        getRequestedFor(urlEqualTo(s"/cases?page=1&page_size=2&$filter"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
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

      verify(
        deleteRequestedFor(urlEqualTo("/cases"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
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

      verify(
        deleteRequestedFor(urlEqualTo("/events"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

  "Connector Delete Case by Reference" should {
    "DELETE from the Case Store" in {
      stubFor(
        delete("/case/ref")
          .willReturn(
            aResponse()
              .withStatus(Status.NO_CONTENT)
          )
      )

      await(connector.deleteCase("ref"))

      verify(
        deleteRequestedFor(urlEqualTo("/case/ref"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

  "Connector Delete Event" should {
    "DELETE from the Case Store" in {
      stubFor(
        delete("/events/ref")
          .willReturn(
            aResponse()
              .withStatus(Status.NO_CONTENT)
          )
      )

      await(connector.deleteCaseEvents("ref"))

      verify(
        deleteRequestedFor(urlEqualTo("/events/ref"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

  "Connector Run Days Elapsed" should {
    "PUT to the Case Store" in {
      stubFor(
        put(urlEqualTo("/scheduler/days-elapsed"))
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "text/html")
              .withStatus(Status.NO_CONTENT)
          )
      )

      await(connector.runDaysElapsed)

      verify(
        putRequestedFor(urlEqualTo("/scheduler/days-elapsed"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

  "Connector Run Referred Days Elapsed" should {
    "PUT to the Case Store" in {
      stubFor(
        put(urlEqualTo("/scheduler/referred-days-elapsed"))
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "text/html")
              .withStatus(Status.NO_CONTENT)
          )
      )

      await(connector.runReferredDaysElapsed)

      verify(
        putRequestedFor(urlEqualTo("/scheduler/referred-days-elapsed"))
          .withHeader("X-Api-Token", equalTo(realConfig.apiToken))
      )
    }
  }

}
