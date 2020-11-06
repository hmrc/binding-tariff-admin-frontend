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
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, anyString, refEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.Files.TemporaryFile
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.connector._
import uk.gov.hmrc.bindingtariffadminfrontend.lock.MigrationLock
import uk.gov.hmrc.bindingtariffadminfrontend.model.Cases.btiApplicationExample
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification._
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileSearch, FileUploaded, UploadMigrationDataRequest, UploadTemplate}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.MigrationRepository
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.lock.LockRepository

import scala.collection.JavaConverters
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class DataMigrationServiceTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val repository = mock[MigrationRepository]
  private val caseConnector = mock[BindingTariffClassificationConnector]
  private val fileConnector = mock[FileStoreConnector]
  private val rulingConnector = mock[RulingConnector]
  private val upscanS3Connector = mock[UpscanS3Connector]
  private val dataMigrationConnector = mock[DataMigrationJsonConnector]
  private val appConfig = mock[AppConfig]
  private val lockRepository = mock[LockRepository]
  private def migrationLock = new MigrationLock(lockRepository, appConfig)
  private def actorSystem = ActorSystem.create("testActorSystem")
  private def withService(test: DataMigrationService => Any) = test(new DataMigrationService(
    repository = repository,
    migrationLock = migrationLock,
    fileConnector = fileConnector,
    upscanS3Connector = upscanS3Connector,
    rulingConnector = rulingConnector,
    caseConnector = caseConnector,
    dataMigrationConnector = dataMigrationConnector,
    actorSystem = actorSystem
  ))

  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(repository, caseConnector, fileConnector, rulingConnector, upscanS3Connector, dataMigrationConnector, appConfig, lockRepository)
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    when(appConfig.dataMigrationLockLifetime) thenReturn FiniteDuration(60, TimeUnit.SECONDS)
    when(lockRepository.lock(anyString(), anyString(), any())) thenReturn Future.successful(true)
    when(lockRepository.releaseLock(anyString(), anyString())) thenReturn Future.successful(())
  }

  "Service getDataMigrationFilesDetails" should {
    val aSuccessfullyUploadedFile = FileUploaded("name", "published", "text/plain", None, None)

    "if connector is returning the file metadata" in withService { service =>

      given(fileConnector.find(refEq("name"))(any[HeaderCarrier])).willReturn(Future.successful(Some(aSuccessfullyUploadedFile)))

      val result = await(service.getDataMigrationFilesDetails(List("name")))

      result shouldBe List(aSuccessfullyUploadedFile)
      verify(fileConnector, atLeastOnce()).find(refEq("name"))(any[HeaderCarrier])
    }

    "if connector is returning the file metadata for mutiple callers" in withService { service =>

      given(fileConnector.find(refEq("name1"))(any[HeaderCarrier])).willReturn(Future.successful(Some(aSuccessfullyUploadedFile)))
      given(fileConnector.find(refEq("name2"))(any[HeaderCarrier])).willReturn(Future.successful(Some(aSuccessfullyUploadedFile)))

      val result = await(service.getDataMigrationFilesDetails(List("name1", "name2")))

      result shouldBe List(aSuccessfullyUploadedFile, aSuccessfullyUploadedFile)
      verify(fileConnector, atLeastOnce()).find(refEq("name1"))(any[HeaderCarrier])
      verify(fileConnector, atLeastOnce()).find(refEq("name2"))(any[HeaderCarrier])
    }

    "if connector is not returning the file metadata" in withService { service =>

      given(fileConnector.find(any[String])(any[HeaderCarrier])).willReturn(Future.successful(None))

      intercept[RuntimeException]{
        await(service.getDataMigrationFilesDetails(List("name")))
      }
      verify(fileConnector, atLeastOnce()).find(refEq("name"))(any[HeaderCarrier])
    }
  }

  "Service 'getAvailableFileDetails'" should {
    val file1 = FileUploaded("id1", "name1", "text/plain", None, None)
    val file3 = FileUploaded("id3", "name3", "text/plain", None, None)

    "return the available files" in withService { service =>
      given(fileConnector.find(refEq("id1"))(any[HeaderCarrier])).willReturn(Future.successful(Some(file1)))
      given(fileConnector.find(refEq("id2"))(any[HeaderCarrier])).willReturn(Future.successful(None))
      given(fileConnector.find(refEq("id3"))(any[HeaderCarrier])).willReturn(Future.successful(Some(file3)))

      val result = await(service.getAvailableFileDetails(List("id1", "id2", "id3")))

      result shouldBe List(file1, file3)

      verify(fileConnector, atLeastOnce()).find(refEq("id1"))(any[HeaderCarrier])
      verify(fileConnector, atLeastOnce()).find(refEq("id2"))(any[HeaderCarrier])
      verify(fileConnector, atLeastOnce()).find(refEq("id3"))(any[HeaderCarrier])
    }
  }

  "Service 'Counts'" should {

    "Delegate to Repository" in withService { service =>
      val counts = mock[MigrationCounts]
      given(repository.countByStatus) willReturn Future.successful(counts)
      await(service.counts) shouldBe counts
    }
  }

  "Service 'Get State'" should {
    val migration = mock[Migration]
    val migrations = Paged(Seq(migration))

    "Delegate to Repository" in withService { service =>
      given(repository.get(Seq.empty, Pagination())) willReturn Future.successful(migrations)
      await(service.getState(Seq.empty, Pagination())) shouldBe migrations
    }
  }

  "Service 'Get Next Unprocessed'" should {
    val migration = mock[Migration]

    "Delegate to Repository" in withService { service =>
      given(repository.get(MigrationStatus.UNPROCESSED)) willReturn Future.successful(Some(migration))
      await(service.getNextMigration) shouldBe Some(migration)
    }
  }

  "Service 'Prepare Migration'" should {
    val `case` = mock[MigratableCase]

    "Delegate to Repository" in withService { service =>
      given(repository.delete(any[Seq[Migration]])) willReturn Future.successful(true)
      given(repository.insert(any[Seq[Migration]])) willReturn Future.successful(true)

      await(service.prepareMigration(Source(List(`case`)))) shouldBe true

      theMigrationsCreated shouldBe Seq(
        Migration(`case`, MigrationStatus.UNPROCESSED)
      )

      theMigrationsDeleted shouldBe Seq(
        Migration(`case`, MigrationStatus.UNPROCESSED)
      )
    }

    def theMigrationsCreated: Seq[Migration] = {
      val captor: ArgumentCaptor[Seq[Migration]] = ArgumentCaptor.forClass(classOf[Seq[Migration]])
      verify(repository).insert(captor.capture())
      captor.getValue
    }

    def theMigrationsDeleted: Seq[Migration] = {
      val captor: ArgumentCaptor[Seq[Migration]] = ArgumentCaptor.forClass(classOf[Seq[Migration]])
      verify(repository).delete(captor.capture())
      captor.getValue
    }
  }

  "Service 'Update'" should {
    val migration = mock[Migration]
    val migrationUpdated = mock[Migration]

    "Delegate to Repository" in withService { service =>
      given(repository.update(migration)) willReturn Future.successful(Some(migrationUpdated))

      await(service.update(migration)) shouldBe Some(migrationUpdated)
    }
  }

  "Service 'Clear Environment'" should {
    val stores = Store.values

    "Clear Back Ends" in withService { service =>
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Clear Files" in withService { service =>
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.FILES)))

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(repository, never()).delete(None)
    }

    "Clear Cases" in withService { service =>
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.CASES)))

      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(repository, never()).delete(None)
    }

    "Clear Events" in withService { service =>
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.EVENTS)))

      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(repository, never()).delete(None)
    }

    "Clear Rulings" in withService { service =>
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.RULINGS)))

      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(repository, never()).delete(None)
    }

    "Clear Historic Data" in withService { service =>
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.HISTORIC_DATA)))

      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(repository, never()).delete(None)
    }

    "Clear Migrations" in withService { service =>
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment(Set(Store.MIGRATION)))

      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle FileStore Failure" in withService { service =>
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle CaseStore Case Delete Failure " in withService { service =>
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle CaseStore Event Delete Failure" in withService { service =>
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle Migrations Delete Failure" in withService { service =>
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.failed(new RuntimeException("Error"))

      await(service.resetEnvironment(stores))

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle Ruling Delete Failure" in withService { service =>
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle Historic Data Delete Failure" in withService { service =>
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(repository).delete(None)
    }
  }

  "Service 'Process'" should {
    val migratableAttachment = MigratedAttachment(public = true, name = "name", timestamp = Instant.EPOCH)
    val migratableEvent1 = MigratableEvent(details = Note("note"), operator = Operator("id"), timestamp = Instant.MAX)
    val migratedEvent1 = Event(details = Note("note"), caseReference = "1", operator = Operator("id"), timestamp = Instant.MAX)
    val migratableEvent2 = MigratableEvent(details = Note("other"), operator = Operator("id"), timestamp = Instant.MAX)
    val migratedEvent2 = Event(details = Note("other"), caseReference = "1", operator = Operator("id"), timestamp = Instant.MAX)
    val migratableCase = MigratableCase("1", CaseStatus.OPEN, Instant.EPOCH, 0, Some(0), None, None, None, None, btiApplicationExample, None, Seq.empty, Seq.empty, Set("keyword1", "keyword2"))
    val migratableCaseWithEvents = MigratableCase("1", CaseStatus.OPEN, Instant.EPOCH, 0, Some(0), None, None, None, None, btiApplicationExample, None, Seq.empty, Seq(migratableEvent1, migratableEvent2), Set("keyword1", "keyword2"))
    val migratableCaseWithAttachments = MigratableCase("1", CaseStatus.OPEN, Instant.EPOCH, 0, Some(0), None, None, None, None, btiApplicationExample, None, Seq(migratableAttachment), Seq.empty, Set("keyword1", "keyword2"))

    val attachment = Attachment(id = "name", public = true, timestamp = Instant.EPOCH)
    val aCase = Case("1", CaseStatus.OPEN, Instant.EPOCH, 0, 0, None, None, None, None, btiApplicationExample, None, Seq.empty, Set("keyword1", "keyword2"))
    val aCaseWithAttachments = Case("1", CaseStatus.OPEN, Instant.EPOCH, 0, 0, None, None, None, None, btiApplicationExample, None, Seq(attachment), Set("keyword1", "keyword2"))

    val anUnprocessedMigration = Migration(migratableCase)
    val anUnprocessedMigrationWithAttachments = Migration(migratableCaseWithAttachments)
    val anUnprocessedMigrationWithEvents = Migration(migratableCaseWithEvents)

    "Migrate new Case" in withService { service =>
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturnsItself()
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      theCaseCreated shouldBe aCase
    }

    "Migrate new Case - with Ruling Store failure" in withService { service =>
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturnsItself()
      givenNotifyingTheRulingStoreFails()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.PARTIAL_SUCCESS
      migrated.message shouldBe Seq("Failed to notify the ruling store [Notify Error]")

      theCaseCreated shouldBe aCase
    }

    "Migrate new Case with Attachments - with no migrated files found" in withService { service =>
      givenTheCaseDoesNotAlreadyExist()
      givenRetrievingTheUploadedFilesReturnsNone()
      givenUpsertingTheCaseReturnsItself()
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigrationWithAttachments))
      migrated.status shouldBe MigrationStatus.PARTIAL_SUCCESS
      migrated.message shouldBe Seq(
        "Failed to migrate 1/1 attachments",
        "Failed to migrate file [name] because [Not Found]"
      )

      theCaseCreated shouldBe aCase
    }

    "Migrate new Case with Attachments - with publish failure" in withService { service =>
      val aSuccessfullyUploadedFile = FileUploaded("name", "published", "text/plain", None, None)

      givenTheCaseDoesNotAlreadyExist()
      givenRetrievingTheUploadedFilesReturns(aSuccessfullyUploadedFile)
      givenUpsertingTheCaseReturnsItself()
      givenPublishingTheFileFails()
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigrationWithAttachments))
      migrated.status shouldBe MigrationStatus.PARTIAL_SUCCESS
      migrated.message shouldBe Seq(
        "Failed to migrate 1/1 attachments",
        "Failed to migrate file [name] because [Publish Error]"
      )

      theCaseCreated shouldBe aCaseWithAttachments
    }

    "Migrate new Case with Attachments - with pre-published file" in withService { service =>
      val aSuccessfullyUploadedFile = FileUploaded("name", "published", "text/plain", None, None, published = true)

      givenTheCaseDoesNotAlreadyExist()
      givenRetrievingTheUploadedFilesReturns(aSuccessfullyUploadedFile)
      givenUpsertingTheCaseReturnsItself()
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigrationWithAttachments))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      theCaseCreated shouldBe aCaseWithAttachments

      verify(fileConnector, never()).publish(any[String])(any[HeaderCarrier])
    }

    "Migrate existing Case with Attachments" in withService { service =>
      val aSuccessfullyUploadedFile = FileUploaded("name", "published", "text/plain", None, None)
      val aSuccessfullyPublishedFile = FileUploaded("name", "published", "text/plain", None, None, published = true)

      givenTheCaseExistsWithoutAttachments()
      givenTheCaseExistsWithoutEvents()
      givenRetrievingTheUploadedFilesReturns(aSuccessfullyUploadedFile)
      givenUpsertingTheCaseReturnsItself()
      givenPublishingTheFileReturns(aSuccessfullyPublishedFile)
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigrationWithAttachments))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      theCaseCreated shouldBe aCaseWithAttachments
    }

    "Migrate existing case with Attachments - removing missing attachments" in withService { service =>
      val anExistingFile = FileUploaded("attachment-id", "published", "text/plain", None, None, published = true)
      val aSuccessfullyUploadedFile = FileUploaded("name", "published", "text/plain", None, None)
      val aSuccessfullyPublishedFile = FileUploaded("name", "published", "text/plain", None, None, published = true)

      givenTheCaseExistsWithAttachment("attachment-id")
      givenTheCaseExistsWithoutEvents()
      givenUpsertingTheCaseReturnsItself()
      givenRetrievingTheUploadedFilesReturns(aSuccessfullyUploadedFile, anExistingFile)
      givenPublishingTheFileReturns(aSuccessfullyPublishedFile)
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      theCaseCreated shouldBe aCase
      verify(fileConnector).delete("attachment-id")
    }

    "Migrate existing Case with Events - ignoring existing events" in withService { service =>
      givenTheCaseExistsWithoutAttachments()
      givenTheCaseExistsWithEvents(migratedEvent1)
      givenUpsertingTheCaseReturnsItself()
      givenCreatingAnEventReturnsItself()
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigrationWithEvents))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      theCaseCreated shouldBe aCase
      theEventsCreated shouldBe Seq(migratedEvent2)
    }

    "Migrate new Case with attachments - with partial failures" in withService { service =>
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturnsItself()
      givenRetrievingTheUploadedFilesFails()
      givenNotifyingTheRulingStoreFails()

      val migrated = await(service.process(anUnprocessedMigrationWithAttachments))
      migrated.status shouldBe MigrationStatus.PARTIAL_SUCCESS
      migrated.message shouldBe Seq(
        "Failed to migrate 1/1 attachments",
        "Failed to migrate file [name] because [Not Found]",
        "Failed to notify the ruling store [Notify Error]"
      )

      theCaseCreated shouldBe aCase
    }

    "Migrate new Case with events - with partial failures" in withService { service =>
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturnsItself()
      givenCreatingAnEventFails()
      givenNotifyingTheRulingStoreFails()

      val migrated = await(service.process(anUnprocessedMigrationWithEvents))
      migrated.status shouldBe MigrationStatus.PARTIAL_SUCCESS
      migrated.message shouldBe Seq(
        "Failed to migrate 2/2 events",
        "Failed to migrate event [NOTE] because [Create Event Failure]",
        "Failed to migrate event [NOTE] because [Create Event Failure]",
        "Failed to notify the ruling store [Notify Error]"
      )

      theCaseCreated shouldBe aCase
    }

    "Throw Exception on Upsert Failure" in withService { service =>
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseFails()

      intercept[RuntimeException] {
        await(service.process(anUnprocessedMigration))
      }.getMessage shouldBe "Upsert Error"
    }

    def givenTheCaseExistsWithoutAttachments(): Unit = {
      given(caseConnector.getCase(any[String])(any[HeaderCarrier])) willReturn Future.successful(Some(aCase.copy(attachments = Seq())))
    }

    def givenTheCaseExistsWithoutEvents(): Unit = {
      given(caseConnector.getEvents(any[String], any[Pagination])(any[HeaderCarrier])) willReturn Future.successful(Paged.empty[Event])
    }

    def givenCreatingAnEventReturnsItself(): Unit = {
      given(caseConnector.createEvent(anyString(), any[Event])(any[HeaderCarrier])) will new Answer[Future[Event]] {
        override def answer(invocation: InvocationOnMock): Future[Event] = Future.successful(invocation.getArgument(1))
      }
    }

    def givenCreatingAnEventFails(): Unit = {
      given(caseConnector.createEvent(anyString(), any[Event])(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Create Event Failure"))
    }

    def givenTheCaseExistsWithEvents(event: Event*): Unit = {
      given(caseConnector.getEvents(any[String], any[Pagination])(any[HeaderCarrier])) willReturn Future.successful(Paged(event))
    }

    def givenTheCaseExistsWithAttachment(id: String): Unit = {
      val attachment = mock[Attachment]
      val fileUploaded = mock[FileUploaded]
      given(attachment.id) willReturn id
      given(fileUploaded.id) willReturn id
      given(fileConnector.delete(any[String])(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.getCase(any[String])(any[HeaderCarrier])) willReturn Future.successful(Some(aCase.copy(attachments = Seq(attachment))))
    }

    def givenPublishingTheFileReturns(aSuccessfullyUploadedFile: FileUploaded): Unit = {
      given(fileConnector.publish(anyString())(any[HeaderCarrier])) willReturn Future.successful(aSuccessfullyUploadedFile)
    }

    def givenRetrievingTheUploadedFilesFails(): Unit = {
      given(fileConnector.find(any[FileSearch], any[Pagination])(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Find Error"))
    }

    def givenRetrievingTheUploadedFilesReturnsNone(): Unit = {
      given(fileConnector.find(any[FileSearch], any[Pagination])(any[HeaderCarrier])) willReturn Future.successful(Paged.empty[FileUploaded])
    }

    def givenRetrievingTheUploadedFilesReturns(files: FileUploaded*): Unit = {
      given(fileConnector.find(any[FileSearch], any[Pagination])(any[HeaderCarrier])) willReturn Future.successful(Paged(files.toSeq))
    }

    def givenPublishingTheFileFails(): Unit = {
      given(fileConnector.publish(anyString())(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Publish Error"))
    }

    def givenNotifyingTheRulingStoreFails() = {
      given(rulingConnector.notify(anyString())(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Notify Error"))
    }

    def givenNotifyingTheRulingStoreSucceeds() = {
      given(rulingConnector.notify(anyString())(any[HeaderCarrier])) willReturn Future.successful(())
    }

    def givenUpsertingTheCaseReturnsItself(): Unit = {
      given(caseConnector.upsertCase(any[Case])(any[HeaderCarrier])) willAnswer new Answer[Future[Case]] {
        override def answer(invocation: InvocationOnMock): Future[Case] = Future.successful(invocation.getArgument(0))
      }
    }

    def givenUpsertingTheCaseFails() = {
      given(caseConnector.upsertCase(any[Case])(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Upsert Error"))
    }

    def givenTheCaseDoesNotAlreadyExist() = {
      given(caseConnector.getCase(any[String])(any[HeaderCarrier])) willReturn Future.successful(None)
      given(caseConnector.getEvents(any[String], any[Pagination])(any[HeaderCarrier])) willReturn Future.failed(new NotFoundException("events not found"))
    }

    def theCaseCreated: Case = {
      val captor: ArgumentCaptor[Case] = ArgumentCaptor.forClass(classOf[Case])
      verify(caseConnector).upsertCase(captor.capture())(any())
      captor.getValue
    }

    def theEventsCreated: Seq[Event] = {
      val captor: ArgumentCaptor[Event] = ArgumentCaptor.forClass(classOf[Event])
      verify(caseConnector, atLeastOnce()).createEvent(any[String], captor.capture())(any())
      captor.getAllValues
    }

    def verifyNoEventsCreated = {
      verify(caseConnector, never()).createEvent(anyString(), any[Event])(any[HeaderCarrier])
    }

    implicit def list2seq[T]: java.util.List[T] => Seq[T] = JavaConverters.asScalaBufferConverter(_).asScala.toSeq

  }

  "Service clear" should {
    "Delegate to repository" in withService { service =>
      given(repository.delete(None)) willReturn Future.successful(true)
      await(service.clear()) shouldBe true
    }

    "Delegate to repository with status" in withService { service =>
      given(repository.delete(Some(MigrationStatus.SUCCESS))) willReturn Future.successful(true)
      await(service.clear(Some(MigrationStatus.SUCCESS))) shouldBe true
    }
  }

  "Service initiate" should {
    "Delegate to connector" in withService { service =>
      val request = UploadMigrationDataRequest("name", "type")
      val template = UploadTemplate("href", Map())

      given(fileConnector.initiate(request)) willReturn Future.successful(template)

      await(service.initiateFileMigration(request)) shouldBe template
    }
  }

  "Service upload" should {
    "Delegate to connector" in withService { service =>
      val request = UploadMigrationDataRequest("name", "type")
      val template = UploadTemplate("href", Map())
      val file = mock[TemporaryFile]

      given(fileConnector.initiate(request)) willReturn Future.successful(template)
      given(upscanS3Connector.upload(template, file, request)) willReturn Future.successful(())

      await(service.initiateFileMigration(request)) shouldBe template
    }
  }

}
