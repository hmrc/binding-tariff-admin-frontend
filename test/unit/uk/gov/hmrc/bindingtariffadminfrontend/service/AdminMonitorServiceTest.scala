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
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification._
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileSearch, FileUploaded}
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.repository.UploadRepository
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class AdminMonitorServiceTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val btcConnector               = mock[BindingTariffClassificationConnector]
  private val fileConnector              = mock[FileStoreConnector]
  private val uploadRepository           = mock[UploadRepository]
  private val service                    = new AdminMonitorService(btcConnector, fileConnector, uploadRepository)

  "getStatistics" should {
    "delegate to connectors" in {
      givenMigratedBtis(1)
      givenMigratedLiabilities(2)
      givenMigratedCorrespondenceCases(3)
      givenMigratedMiscCases(4)
      givenSubmittedAtars(101)
      givenSubmittedLiabilities(102)
      givenSubmittedCorrespondenceCases(103)
      givenSubmittedMiscCases(104)
      givenPublishedFiles(1001)
      givenUnpublishedFiles(2002)
      givenUploadedAttachments(505)

      await(service.getStatistics) shouldBe MonitorStatistics(
        submittedCases = Map(
          ApplicationType.BTI             -> 101,
          ApplicationType.LIABILITY_ORDER -> 102,
          ApplicationType.CORRESPONDENCE  -> 103,
          ApplicationType.MISCELLANEOUS   -> 104
        ),
        migratedCases = Map(
          ApplicationType.BTI             -> 1,
          ApplicationType.LIABILITY_ORDER -> 2,
          ApplicationType.CORRESPONDENCE  -> 3,
          ApplicationType.MISCELLANEOUS   -> 4
        ),
        publishedFileCount      = 1001,
        unpublishedFileCount    = 2002,
        migratedAttachmentCount = 505
      )
    }
  }

  "Get Cases" should {
    "Delegate to connector" in {
      given(btcConnector.getCases(refEq(CaseSearch()), refEq(Pagination()))(any[HeaderCarrier])) willReturn Future
        .successful(Paged.empty[Case])
      await(service.getCases(CaseSearch(), Pagination())) shouldBe Paged.empty[Case]
    }
  }

  "Get Files" should {
    "Delegate to connector" in {
      given(fileConnector.find(refEq(FileSearch()), refEq(Pagination()))(any[HeaderCarrier])) willReturn Future
        .successful(Paged.empty[FileUploaded])
      await(service.getFiles(FileSearch(), Pagination())) shouldBe Paged.empty[FileUploaded]
    }
  }

  "Get Events" should {
    "Delegate to connector" in {
      given(btcConnector.getEvents(refEq(EventSearch()), refEq(Pagination()))(any[HeaderCarrier])) willReturn Future
        .successful(Paged.empty[Event])
      await(service.getEvents(EventSearch(), Pagination())) shouldBe Paged.empty[Event]
    }
  }

  "Run Days Elapsed" should {
    "Delegate to connector" in {
      given(btcConnector.runDaysElapsed(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      await(service.runScheduledJob(ScheduledJob.DAYS_ELAPSED)) shouldBe (): Unit
    }
  }

  "Run Referred Days Elapsed" should {
    "Delegate to connector" in {
      given(btcConnector.runReferredDaysElapsed(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      await(service.runScheduledJob(ScheduledJob.REFERRED_DAYS_ELAPSED)) shouldBe (): Unit
    }
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(btcConnector, fileConnector, uploadRepository)
  }

  private def givenMigratedBtis(count: Int): Unit =
    given(
      btcConnector.getCases(
        refEq(CaseSearch(migrated = Some(true), applicationType = Some(ApplicationType.BTI))),
        refEq(Pagination(1, 1))
      )(any[HeaderCarrier])
    ) willReturn Future.successful(Paged(Seq.empty[Case], 0, 0, count))

  private def givenMigratedLiabilities(count: Int): Unit =
    given(
      btcConnector.getCases(
        refEq(CaseSearch(migrated = Some(true), applicationType = Some(ApplicationType.LIABILITY_ORDER))),
        refEq(Pagination(1, 1))
      )(any[HeaderCarrier])
    ) willReturn Future.successful(Paged(Seq.empty[Case], 0, 0, count))

  private def givenMigratedCorrespondenceCases(count: Int): Unit =
    given(
      btcConnector.getCases(
        refEq(CaseSearch(migrated = Some(true), applicationType = Some(ApplicationType.CORRESPONDENCE))),
        refEq(Pagination(1, 1))
      )(any[HeaderCarrier])
    ) willReturn Future.successful(Paged(Seq.empty[Case], 0, 0, count))

  private def givenMigratedMiscCases(count: Int): Unit =
    given(
      btcConnector.getCases(
        refEq(CaseSearch(migrated = Some(true), applicationType = Some(ApplicationType.MISCELLANEOUS))),
        refEq(Pagination(1, 1))
      )(any[HeaderCarrier])
    ) willReturn Future.successful(Paged(Seq.empty[Case], 0, 0, count))

  private def givenSubmittedAtars(count: Int): Unit =
    given(
      btcConnector.getCases(
        refEq(CaseSearch(migrated = Some(false), applicationType = Some(ApplicationType.BTI))),
        refEq(Pagination(1, 1))
      )(any[HeaderCarrier])
    ) willReturn Future.successful(Paged(Seq.empty[Case], 0, 0, count))

  private def givenSubmittedLiabilities(count: Int): Unit =
    given(
      btcConnector.getCases(
        refEq(CaseSearch(migrated = Some(false), applicationType = Some(ApplicationType.LIABILITY_ORDER))),
        refEq(Pagination(1, 1))
      )(any[HeaderCarrier])
    ) willReturn Future.successful(Paged(Seq.empty[Case], 0, 0, count))

  private def givenSubmittedCorrespondenceCases(count: Int): Unit =
    given(
      btcConnector.getCases(
        refEq(CaseSearch(migrated = Some(false), applicationType = Some(ApplicationType.CORRESPONDENCE))),
        refEq(Pagination(1, 1))
      )(any[HeaderCarrier])
    ) willReturn Future.successful(Paged(Seq.empty[Case], 0, 0, count))

  private def givenSubmittedMiscCases(count: Int): Unit =
    given(
      btcConnector.getCases(
        refEq(CaseSearch(migrated = Some(false), applicationType = Some(ApplicationType.MISCELLANEOUS))),
        refEq(Pagination(1, 1))
      )(any[HeaderCarrier])
    ) willReturn Future.successful(Paged(Seq.empty[Case], 0, 0, count))

  private def givenPublishedFiles(count: Int): Unit =
    given(
      fileConnector.find(refEq(FileSearch(published = Some(true))), refEq(Pagination(1, 1)))(any[HeaderCarrier])
    ) willReturn Future.successful(Paged(Seq.empty[FileUploaded], 0, 0, count))

  private def givenUnpublishedFiles(count: Int): Unit =
    given(
      fileConnector.find(refEq(FileSearch(published = Some(false))), refEq(Pagination(1, 1)))(any[HeaderCarrier])
    ) willReturn Future.successful(Paged(Seq.empty[FileUploaded], 0, 0, count))

  private def givenUploadedAttachments(count: Int): Unit =
    given(uploadRepository.countType[AttachmentUpload]) willReturn Future.successful(count)
}
