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

package uk.gov.hmrc.bindingtariffadminfrontend.service

import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffadminfrontend.connector.{BindingTariffClassificationConnector, FileStoreConnector}
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.{Case, CaseSearch, Event, EventSearch}
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileSearch, FileUploaded}
import uk.gov.hmrc.bindingtariffadminfrontend.model.{Paged, Pagination, ScheduledJob}
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class AdminMonitorServiceTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val btcConnector = mock[BindingTariffClassificationConnector]
  private val fileConnector = mock[FileStoreConnector]
  private val service = new AdminMonitorService(btcConnector, fileConnector)


  "Count Cases" should {
    "Delegate to connector" in {
      given(btcConnector.getCases(refEq(CaseSearch()), refEq(Pagination(1, 1)))(any[HeaderCarrier])) willReturn Future.successful(Paged(Seq.empty[Case], 0, 0, 1))
      await(service.countCases) shouldBe 1
    }
  }

  "Count Published Files" should {
    "Delegate to connector" in {
      given(fileConnector.find(refEq(FileSearch(published = Some(true))), refEq(Pagination(1, 1)))(any[HeaderCarrier])) willReturn Future.successful(Paged(Seq.empty[FileUploaded], 0, 0, 1))
      await(service.countPublishedFiles) shouldBe 1
    }
  }

  "Count Unpublished Files" should {
    "Delegate to connector" in {
      given(fileConnector.find(refEq(FileSearch(published = Some(false))), refEq(Pagination(1, 1)))(any[HeaderCarrier])) willReturn Future.successful(Paged(Seq.empty[FileUploaded], 0, 0, 1))
      await(service.countUnpublishedFiles) shouldBe 1
    }
  }

  "Get Cases" should {
    "Delegate to connector" in {
      given(btcConnector.getCases(refEq(CaseSearch()), refEq(Pagination()))(any[HeaderCarrier])) willReturn Future.successful(Paged.empty[Case])
      await(service.getCases(CaseSearch(), Pagination())) shouldBe Paged.empty[Case]
    }
  }

  "Get Files" should {
    "Delegate to connector" in {
      given(fileConnector.find(refEq(FileSearch()), refEq(Pagination()))(any[HeaderCarrier])) willReturn Future.successful(Paged.empty[FileUploaded])
      await(service.getFiles(FileSearch(), Pagination())) shouldBe Paged.empty[FileUploaded]
    }
  }

  "Get Events" should {
    "Delegate to connector" in {
      given(btcConnector.getEvents(refEq(EventSearch()), refEq(Pagination()))(any[HeaderCarrier])) willReturn Future.successful(Paged.empty[Event])
      await(service.getEvents(EventSearch(), Pagination())) shouldBe Paged.empty[Event]
    }
  }

  "Run Days Elapsed" should {
    "Delegate to connector" in {
      given(btcConnector.runDaysElapsed(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      await(service.runScheduledJob(ScheduledJob.DAYS_ELAPSED)) shouldBe (): Unit
    }
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(btcConnector, fileConnector)
  }
}
