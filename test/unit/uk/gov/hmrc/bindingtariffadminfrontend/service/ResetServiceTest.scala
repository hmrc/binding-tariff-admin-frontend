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

import java.time.Instant

import akka.actor.ActorSystem
import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffadminfrontend.connector._
import uk.gov.hmrc.bindingtariffadminfrontend.model.Cases.btiApplicationExample
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigrationStatus.MigrationStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.{Attachment, Case, CaseSearch, CaseStatus}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.UploadRepository
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class ResetServiceTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val uploadRepository       = mock[UploadRepository]
  private val caseConnector          = mock[BindingTariffClassificationConnector]
  private val fileConnector          = mock[FileStoreConnector]
  private val rulingConnector        = mock[RulingConnector]
  private val dataMigrationConnector = mock[DataMigrationJsonConnector]
  private val dataMigrationService   = mock[DataMigrationService]

  private def actorSystem = ActorSystem.create("testActorSystem")

  private def withService(test: ResetService => Any) =
    test(
      new ResetService(
        uploadRepository       = uploadRepository,
        fileConnector          = fileConnector,
        rulingConnector        = rulingConnector,
        caseConnector          = caseConnector,
        dataMigrationConnector = dataMigrationConnector,
        dataMigrationService   = dataMigrationService,
        actorSystem            = actorSystem
      )
    )

  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(
      uploadRepository,
      caseConnector,
      fileConnector,
      rulingConnector,
      dataMigrationConnector,
      dataMigrationService
    )
  }

  override protected def beforeEach(): Unit =
    super.beforeEach()

  "resetEnvironment" should {
    val stores = Store.values

    "Clear Back Ends" in withService { service =>
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Clear Files" in withService { service =>
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.FILES)))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService, never()).clear(any[Option[MigrationStatus]])
    }

    "Clear Cases" in withService { service =>
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.CASES)))

      verify(uploadRepository, never()).deleteAll()
      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService, never()).clear(any[Option[MigrationStatus]])
    }

    "Clear Events" in withService { service =>
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.EVENTS)))

      verify(uploadRepository, never()).deleteAll()
      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService, never()).clear(any[Option[MigrationStatus]])
    }

    "Clear Rulings" in withService { service =>
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.RULINGS)))

      verify(uploadRepository, never()).deleteAll()
      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService, never()).clear(any[Option[MigrationStatus]])
    }

    "Clear Historic Data" in withService { service =>
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.HISTORIC_DATA)))

      verify(uploadRepository, never()).deleteAll()
      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService, never()).clear(any[Option[MigrationStatus]])
    }

    "Clear Migrations" in withService { service =>
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(Set(Store.MIGRATION)))

      verify(uploadRepository, never()).deleteAll()
      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Handle UploadRepository Failure" in withService { service =>
      given(uploadRepository.deleteAll()) willReturn Future.failed(new RuntimeException("Error"))
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Handle FileStore Failure" in withService { service =>
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Handle CaseStore Case Delete Failure " in withService { service =>
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Handle CaseStore Event Delete Failure" in withService { service =>
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Handle Migrations Delete Failure" in withService { service =>
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationService.clear(refEq(None))) willReturn Future.failed(new RuntimeException("Error"))

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Handle Ruling Delete Failure" in withService { service =>
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Handle Historic Data Delete Failure" in withService { service =>
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.failed(
        new RuntimeException("Error")
      )
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }
  }

  "resetMigratedCases" should {
    val extractDate = Instant.ofEpochSecond(1609416000)
    val attachment1 = Attachment(id = "attachment_id_1", public = true, timestamp = Instant.EPOCH)
    val attachment2 = Attachment(id = "attachment_id_2", public = true, timestamp = Instant.EPOCH)
    val aCase = Case(
      "ref1",
      CaseStatus.OPEN,
      Instant.EPOCH,
      0,
      0,
      None,
      None,
      None,
      None,
      btiApplicationExample,
      None,
      Seq.empty,
      Set("keyword1", "keyword2"),
      dateOfExtract = Some(extractDate)
    )
    val aCaseWithAttachments = Case(
      "ref2",
      CaseStatus.COMPLETED,
      Instant.EPOCH,
      0,
      0,
      None,
      None,
      None,
      None,
      btiApplicationExample,
      None,
      Seq(attachment1, attachment2),
      Set("keyword1", "keyword2"),
      dateOfExtract = Some(extractDate)
    )

    val caseSearch = CaseSearch(migrated = Some(true))
    val pagination = Pagination(1, 512)

    val success: Future[Unit] = Future.successful((): Unit)
    val failure: Future[Unit] = Future.failed(new RuntimeException("simulated error"))

    "clear migrations" in withService { service =>
      val cases = Future.successful(
        Paged[Case](results = Seq.empty[Case], pageIndex = 1, pageSize = 1, resultCount = 0)
      )

      given(caseConnector.getCases(refEq(caseSearch), refEq(pagination))(any[HeaderCarrier])) willReturn cases

      givenMigrationsClearSuccessfully()
      val result = await(service.resetMigratedCases())
      verifyMigrationsCleared()

      result shouldBe 0

      verify(rulingConnector, never()).delete(any[String])(any[HeaderCarrier])
      verify(fileConnector, never()).delete(any[String])(any[HeaderCarrier])
      verify(uploadRepository, never()).deleteById(any[String])
      verify(caseConnector, never()).deleteCaseEvents(any[String])(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCase(any[String])(any[HeaderCarrier])
    }

    "delete a case without attachments" in withService { service =>
      val cases = Future.successful(
        Paged[Case](results = Seq(aCase), pageIndex = 1, pageSize = 1, resultCount = 1)
      )

      given(caseConnector.getCases(refEq(caseSearch), refEq(pagination))(any[HeaderCarrier])) willReturn cases
      given(rulingConnector.delete(refEq(aCase.reference))(any[HeaderCarrier])) willReturn success
      given(caseConnector.deleteCaseEvents(refEq(aCase.reference))(any[HeaderCarrier])) willReturn success
      given(caseConnector.deleteCase(refEq(aCase.reference))(any[HeaderCarrier])) willReturn success

      givenMigrationsClearSuccessfully()
      val result = await(service.resetMigratedCases())
      verifyMigrationsCleared()

      result shouldBe 1

      verify(rulingConnector).delete(refEq(aCase.reference))(any[HeaderCarrier])
      verify(fileConnector, never()).delete(any[String])(any[HeaderCarrier])
      verify(uploadRepository, never()).deleteById(any[String])
      verify(caseConnector).deleteCaseEvents(refEq(aCase.reference))(any[HeaderCarrier])
      verify(caseConnector).deleteCase(refEq(aCase.reference))(any[HeaderCarrier])
    }

    "delete a case with attachments" in withService { service =>
      val cases = Future.successful(
        Paged[Case](results = Seq(aCaseWithAttachments), pageIndex = 1, pageSize = 1, resultCount = 1)
      )

      given(caseConnector.getCases(refEq(caseSearch), refEq(pagination))(any[HeaderCarrier])) willReturn cases
      given(rulingConnector.delete(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])) willReturn success
      given(fileConnector.delete(refEq(attachment1.id))(any[HeaderCarrier])) willReturn success
      given(fileConnector.delete(refEq(attachment2.id))(any[HeaderCarrier])) willReturn success
      given(uploadRepository.deleteById(refEq(attachment1.id))) willReturn success
      given(uploadRepository.deleteById(refEq(attachment2.id))) willReturn success
      given(caseConnector.deleteCaseEvents(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])) willReturn success
      given(caseConnector.deleteCase(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])) willReturn success

      givenMigrationsClearSuccessfully()
      val result = await(service.resetMigratedCases())
      verifyMigrationsCleared()

      result shouldBe 1

      verify(rulingConnector).delete(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])
      verify(fileConnector).delete(refEq(attachment1.id))(any[HeaderCarrier])
      verify(fileConnector).delete(refEq(attachment2.id))(any[HeaderCarrier])
      verify(uploadRepository).deleteById(refEq(attachment1.id))
      verify(uploadRepository).deleteById(refEq(attachment2.id))
      verify(caseConnector).deleteCaseEvents(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])
      verify(caseConnector).deleteCase(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])
    }

    "delete cases from multiple pages" in withService { service =>
      val page1 = Future.successful(
        Paged[Case](results = Seq(aCase), pageIndex = 1, pageSize = 1, resultCount = 2)
      )
      val page2 = Future.successful(
        Paged[Case](results = Seq(aCaseWithAttachments), pageIndex = 2, pageSize = 1, resultCount = 2)
      )

      given(caseConnector.getCases(refEq(caseSearch), refEq(pagination))(any[HeaderCarrier])) willReturn page1
      given(caseConnector.getCases(refEq(caseSearch), refEq(pagination.copy(page = 2)))(any[HeaderCarrier])) willReturn page2

      given(rulingConnector.delete(refEq(aCase.reference))(any[HeaderCarrier])) willReturn success
      given(rulingConnector.delete(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])) willReturn success
      given(fileConnector.delete(refEq(attachment1.id))(any[HeaderCarrier])) willReturn success
      given(fileConnector.delete(refEq(attachment2.id))(any[HeaderCarrier])) willReturn success
      given(uploadRepository.deleteById(refEq(attachment1.id))) willReturn success
      given(uploadRepository.deleteById(refEq(attachment2.id))) willReturn success
      given(caseConnector.deleteCaseEvents(refEq(aCase.reference))(any[HeaderCarrier])) willReturn success
      given(caseConnector.deleteCaseEvents(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])) willReturn success
      given(caseConnector.deleteCase(refEq(aCase.reference))(any[HeaderCarrier])) willReturn success
      given(caseConnector.deleteCase(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])) willReturn success

      givenMigrationsClearSuccessfully()
      val result = await(service.resetMigratedCases())
      verifyMigrationsCleared()

      result shouldBe 2

      verify(rulingConnector).delete(refEq(aCase.reference))(any[HeaderCarrier])
      verify(rulingConnector).delete(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])
      verify(fileConnector).delete(refEq(attachment1.id))(any[HeaderCarrier])
      verify(fileConnector).delete(refEq(attachment2.id))(any[HeaderCarrier])
      verify(uploadRepository).deleteById(refEq(attachment1.id))
      verify(uploadRepository).deleteById(refEq(attachment2.id))
      verify(caseConnector).deleteCaseEvents(refEq(aCase.reference))(any[HeaderCarrier])
      verify(caseConnector).deleteCaseEvents(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])
      verify(caseConnector).deleteCase(refEq(aCase.reference))(any[HeaderCarrier])
      verify(caseConnector).deleteCase(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])
    }

    "delete a case with attachments and ignore errors" in withService { service =>
      val cases = Future.successful(
        Paged[Case](results = Seq(aCaseWithAttachments), pageIndex = 1, pageSize = 1, resultCount = 1)
      )

      given(caseConnector.getCases(refEq(caseSearch), refEq(pagination))(any[HeaderCarrier])) willReturn cases
      given(rulingConnector.delete(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])) willReturn failure
      given(fileConnector.delete(refEq(attachment1.id))(any[HeaderCarrier])) willReturn failure
      given(fileConnector.delete(refEq(attachment2.id))(any[HeaderCarrier])) willReturn failure
      given(uploadRepository.deleteById(refEq(attachment1.id))) willReturn failure
      given(uploadRepository.deleteById(refEq(attachment2.id))) willReturn failure
      given(caseConnector.deleteCaseEvents(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])) willReturn failure
      given(caseConnector.deleteCase(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])) willReturn failure

      givenMigrationsClearSuccessfully()
      val result = await(service.resetMigratedCases())
      verifyMigrationsCleared()

      result shouldBe 1

      verify(rulingConnector).delete(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])
      verify(fileConnector).delete(refEq(attachment1.id))(any[HeaderCarrier])
      verify(fileConnector).delete(refEq(attachment2.id))(any[HeaderCarrier])
      verify(uploadRepository).deleteById(refEq(attachment1.id))
      verify(uploadRepository).deleteById(refEq(attachment2.id))
      verify(caseConnector).deleteCaseEvents(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])
      verify(caseConnector).deleteCase(refEq(aCaseWithAttachments.reference))(any[HeaderCarrier])
    }

    def givenMigrationsClearSuccessfully(): Unit =
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

    def verifyMigrationsCleared(): Unit = {
      verify(uploadRepository, never()).deleteAll()
      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }
  }
}
