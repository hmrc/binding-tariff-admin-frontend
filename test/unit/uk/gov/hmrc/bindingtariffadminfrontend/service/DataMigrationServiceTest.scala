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

package uk.gov.hmrc.bindingtariffadminfrontend.service

import java.time.Instant

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, anyString, refEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.libs.Files.TemporaryFile
import uk.gov.hmrc.bindingtariffadminfrontend.connector.{BindingTariffClassificationConnector, FileStoreConnector, RulingConnector, UpscanS3Connector}
import uk.gov.hmrc.bindingtariffadminfrontend.model.Cases.btiApplicationExample
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification._
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileUploaded, FileSearch, UploadRequest, UploadTemplate}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.MigrationRepository
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.JavaConverters
import scala.concurrent.Future

class DataMigrationServiceTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val repository = mock[MigrationRepository]
  private val caseConnector = mock[BindingTariffClassificationConnector]
  private val fileConnector = mock[FileStoreConnector]
  private val rulingConnector = mock[RulingConnector]
  private val upscanS3Connector = mock[UpscanS3Connector]
  private val service = new DataMigrationService(repository, fileConnector, upscanS3Connector, rulingConnector, caseConnector)
  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(repository, caseConnector, fileConnector, rulingConnector, upscanS3Connector)
  }

  "Service 'Counts'" should {

    "Delegate to Repository" in {
      val counts = mock[MigrationCounts]
      given(repository.countByStatus) willReturn Future.successful(counts)
      await(service.counts) shouldBe counts
    }
  }

  "Service 'Get State'" should {
    val migration = mock[Migration]
    val migrations = Paged(Seq(migration))

    "Delegate to Repository" in {
      given(repository.get(Seq.empty, Pagination())) willReturn Future.successful(migrations)
      await(service.getState(Seq.empty, Pagination())) shouldBe migrations
    }
  }

  "Service 'Get Next Unprocessed'" should {
    val migration = mock[Migration]

    "Delegate to Repository" in {
      given(repository.get(MigrationStatus.UNPROCESSED)) willReturn Future.successful(Some(migration))
      await(service.getNextMigration) shouldBe Some(migration)
    }
  }

  "Service 'Prepare Migration'" should {
    val `case` = mock[MigratableCase]

    "Delegate to Repository" in {
      given(repository.delete(any[Seq[Migration]])) willReturn Future.successful(true)
      given(repository.insert(any[Seq[Migration]])) willReturn Future.successful(true)

      await(service.prepareMigration(Seq(`case`))) shouldBe true

      theMigrationsCreated shouldBe Seq(
        Migration(`case`, MigrationStatus.UNPROCESSED)
      )

      theMigrationsDeleted shouldBe Seq(
        Migration(`case`, MigrationStatus.UNPROCESSED)
      )
    }

    "Service 'Update'" should {
      val migration = mock[Migration]
      val migrationUpdated = mock[Migration]

      "Delegate to Repository" in {
        given(repository.update(migration)) willReturn Future.successful(Some(migrationUpdated))

        await(service.update(migration)) shouldBe Some(migrationUpdated)
      }
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

  "Service 'Clear Environment'" should {
    val stores = Store.values

    "Clear Back Ends" in {
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Clear Files" in {
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.FILES)))

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(repository, never()).delete(None)
    }

    "Clear Cases" in {
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.CASES)))

      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(repository, never()).delete(None)
    }

    "Clear Events" in {
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.EVENTS)))

      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(repository, never()).delete(None)
    }

    "Clear Rulings" in {
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.RULINGS)))

      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(repository, never()).delete(None)
    }

    "Clear Migrations" in {
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment(Set(Store.MIGRATION)))

      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle FileStore Failure" in {
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle CaseStore Case Delete Failure " in {
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle CaseStore Event Delete Failure" in {
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle Migrations Delete Failure" in {
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.failed(new RuntimeException("Error"))

      await(service.resetEnvironment(stores))

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle Ruling Delete Failure" in {
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(repository).delete(None)
    }
  }

  "Service 'Process'" should {
    val migratableAttachment = MigratedAttachment(public = true, name = "name", timestamp = Instant.EPOCH)
    val migratableEvent1 = Event(Note("note"), Operator("id"), Instant.now())
    val migratableEvent2 = Event(Note("other"), Operator("id"), Instant.now())
    val migratableCase = MigratableCase("1", CaseStatus.OPEN, Instant.EPOCH, 0, None, None, None, None, btiApplicationExample, None, Seq.empty, Seq.empty, Set("keyword1", "keyword2"))
    val migratableCaseWithEvents = MigratableCase("1", CaseStatus.OPEN, Instant.EPOCH, 0, None, None, None, None, btiApplicationExample, None, Seq.empty, Seq(migratableEvent1, migratableEvent2), Set("keyword1", "keyword2"))
    val migratableCaseWithAttachments = MigratableCase("1", CaseStatus.OPEN, Instant.EPOCH, 0, None, None, None, None, btiApplicationExample, None, Seq(migratableAttachment), Seq.empty, Set("keyword1", "keyword2"))

    val attachment = Attachment(id = "name", public = true, timestamp = Instant.EPOCH)
    val aCase = Case("1", CaseStatus.OPEN, Instant.EPOCH, 0, None, None, None, None, btiApplicationExample, None, Seq.empty, Set("keyword1", "keyword2"))
    val aCaseWithAttachments = Case("1", CaseStatus.OPEN, Instant.EPOCH, 0, None, None, None, None, btiApplicationExample, None, Seq(attachment), Set("keyword1", "keyword2"))

    val anUnprocessedMigration = Migration(migratableCase)
    val anUnprocessedMigrationWithAttachments = Migration(migratableCaseWithAttachments)
    val anUnprocessedMigrationWithEvents = Migration(migratableCaseWithEvents)

    "Migrate new Case" in {
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      theCaseCreated shouldBe aCase
    }

    "Migrate new Case - with Ruling Store failure" in {
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
      givenNotifyingTheRulingStoreFails()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.PARTIAL_SUCCESS
      migrated.message shouldBe Seq("Failed to notify the ruling store [Notify Error]")

      theCaseCreated shouldBe aCase
    }

    "Migrate new Case with Attachments - with find failure" in {
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
      givenRetrievingTheUplodedFileFails()
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigrationWithAttachments))
      migrated.status shouldBe MigrationStatus.PARTIAL_SUCCESS
      migrated.message shouldBe Seq(
        "Failed to migrate 1/1 attachments",
        "Failed to migrate file [name] because [Find Error]"
      )

      theCaseCreated shouldBe aCaseWithAttachments
    }

    "Migrate new Case with Attachments - with find returning None" in {
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
      givenRetrievingTheUplodedFileReturnsNothing()
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigrationWithAttachments))
      migrated.status shouldBe MigrationStatus.PARTIAL_SUCCESS
      migrated.message shouldBe Seq(
        "Failed to migrate 1/1 attachments",
        "Failed to migrate file [name] because [Not found]"
      )

      theCaseCreated shouldBe aCaseWithAttachments
    }

    "Migrate new Case with Attachments - with publish failure" in {
      val aSuccessfullyUploadedFile = FileUploaded("id", "published", "text/plain", None, None)

      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
      givenRetrievingTheUploadedFileReturns(aSuccessfullyUploadedFile)
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

    "Migrate new Case with Attachments - with pre-published file" in {
      val aSuccessfullyUploadedFile = FileUploaded("id", "published", "text/plain", None, None, published = true)

      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
      givenRetrievingTheUploadedFileReturns(aSuccessfullyUploadedFile)
      givenPublishingTheFileFails()
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigrationWithAttachments))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      theCaseCreated shouldBe aCaseWithAttachments

      verify(fileConnector, never()).publish(any[String])(any[HeaderCarrier])
    }

    "Migrate existing Case with Attachments" in {
      val aSuccessfullyUploadedFile = FileUploaded("id", "published", "text/plain", None, None)
      val aSuccessfullyPublishedFile = FileUploaded("id", "published", "text/plain", None, None, published = true)

      givenTheCaseExistsWithoutAttachments()
      givenTheCaseExistsWithoutEvents()
      givenUpsertingTheCaseReturns(aCase)
      givenRetrievingTheUploadedFileReturns(aSuccessfullyUploadedFile)
      givenPublishingTheFileReturns(aSuccessfullyPublishedFile)
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigrationWithAttachments))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      theCaseCreated shouldBe aCaseWithAttachments
    }

    "Migrate existing case with Attachments - removing missing attachments" in {
      val aSuccessfullyUploadedFile = FileUploaded("id", "published", "text/plain", None, None)
      val aSuccessfullyPublishedFile = FileUploaded("id", "published", "text/plain", None, None, published = true)

      givenTheCaseExistsWithAttachment("attachment-id")
      givenTheCaseExistsWithoutEvents()
      givenUpsertingTheCaseReturns(aCase)
      givenRetrievingTheUploadedFileReturns(aSuccessfullyUploadedFile)
      givenPublishingTheFileReturns(aSuccessfullyPublishedFile)
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      theCaseCreated shouldBe aCase
      verify(fileConnector).delete("attachment-id")
    }

    "Migrate existing Case with Events - ignoring existing events" in {
      givenTheCaseExistsWithoutAttachments()
      givenTheCaseExistsWithEvents(migratableEvent1)
      givenUpsertingTheCaseReturns(aCase)
      givenCreatingAnEventReturnsItself()
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigrationWithEvents))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      theCaseCreated shouldBe aCase
      theEventsCreated shouldBe Seq(migratableEvent2)
    }

    "Migrate new Case with attachments - with partial failures" in {
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
      givenRetrievingTheUplodedFileFails()
      givenNotifyingTheRulingStoreFails()

      val migrated = await(service.process(anUnprocessedMigrationWithAttachments))
      migrated.status shouldBe MigrationStatus.PARTIAL_SUCCESS
      migrated.message shouldBe Seq(
        "Failed to migrate 1/1 attachments",
        "Failed to migrate file [name] because [Find Error]",
        "Failed to notify the ruling store [Notify Error]"
      )

      theCaseCreated shouldBe aCaseWithAttachments
    }

    "Migrate new Case with events - with partial failures" in {
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
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

    "Throw Exception on Upsert Failure" in {
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
      given(caseConnector.getEvents(any[String])(any[HeaderCarrier])) willReturn Future.successful(Seq.empty[Event])
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
      given(caseConnector.getEvents(any[String])(any[HeaderCarrier])) willReturn Future.successful(event.toSeq)
    }

    def givenTheCaseExistsWithAttachment(id: String): Unit = {
      val attachment = mock[Attachment]
      val fileUploaded = mock[FileUploaded]
      given(attachment.id) willReturn id
      given(fileUploaded.id) willReturn id
      given(fileConnector.find(any[FileSearch], refEq(Pagination.max))(any[HeaderCarrier])) willReturn Future.successful(Paged(Seq(fileUploaded)))
      given(fileConnector.delete(any[String])(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.getCase(any[String])(any[HeaderCarrier])) willReturn Future.successful(Some(aCase.copy(attachments = Seq(attachment))))
    }

    def givenPublishingTheFileReturns(aSuccessfullyUploadedFile: FileUploaded) = {
      given(fileConnector.publish(anyString())(any[HeaderCarrier])) willReturn Future.successful(aSuccessfullyUploadedFile)
    }

    def givenRetrievingTheUploadedFileReturns(file: FileUploaded) = {
      given(fileConnector.find(anyString())(any[HeaderCarrier])) willReturn Future.successful(Some(file))
    }

    def givenRetrievingTheUplodedFileReturnsNothing() = {
      given(fileConnector.find(anyString())(any[HeaderCarrier])) willReturn Future.successful(None)
    }

    def givenRetrievingTheUplodedFileFails() = {
      given(fileConnector.find(anyString())(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Find Error"))
    }

    def givenPublishingTheFileFails() = {
      given(fileConnector.publish(anyString())(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Publish Error"))
    }

    def givenNotifyingTheRulingStoreFails() = {
      given(rulingConnector.notify(anyString())(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Notify Error"))
    }

    def givenNotifyingTheRulingStoreSucceeds() = {
      given(rulingConnector.notify(anyString())(any[HeaderCarrier])) willReturn Future.successful(())
    }

    def givenUpsertingTheCaseReturns(aCase: Case) = {
      given(caseConnector.upsertCase(any[Case])(any[HeaderCarrier])) willReturn Future.successful(aCase)
    }

    def givenUpsertingTheCaseFails() = {
      given(caseConnector.upsertCase(any[Case])(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Upsert Error"))
    }

    def givenTheCaseDoesNotAlreadyExist() = {
      given(caseConnector.getCase(any[String])(any[HeaderCarrier])) willReturn Future.successful(None)
      given(caseConnector.getEvents(any[String])(any[HeaderCarrier])) willReturn Future.failed(new NotFoundException("events not found"))
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
    "Delegate to repository" in {
      given(repository.delete(None)) willReturn Future.successful(true)
      await(service.clear()) shouldBe true
    }

    "Delegate to repository with status" in {
      given(repository.delete(Some(MigrationStatus.SUCCESS))) willReturn Future.successful(true)
      await(service.clear(Some(MigrationStatus.SUCCESS))) shouldBe true
    }
  }

  "Service initiate" should {
    "Delegate to connector" in {
      val request = UploadRequest("name", "type")
      val template = UploadTemplate("href", Map())

      given(fileConnector.initiate(request)) willReturn Future.successful(template)

      await(service.initiateFileMigration(request)) shouldBe template
    }
  }

  "Service upload" should {
    "Delegate to connector" in {
      val request = UploadRequest("name", "type")
      val template = UploadTemplate("href", Map())
      val file = mock[TemporaryFile]

      given(fileConnector.initiate(request)) willReturn Future.successful(template)
      given(upscanS3Connector.upload(template, file)) willReturn Future.successful(())

      await(service.initiateFileMigration(request)) shouldBe template
    }
  }

}
