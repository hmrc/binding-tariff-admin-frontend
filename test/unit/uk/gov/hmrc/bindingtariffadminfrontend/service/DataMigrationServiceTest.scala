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
import org.mockito.ArgumentMatchers.{any, anyString}
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
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileUploaded, UploadRequest, UploadTemplate}
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
    reset(repository, caseConnector, fileConnector, rulingConnector)
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
    val migrations = Seq(migration)

    "Delegate to Repository" in {
      given(repository.get(0, 1, Seq.empty)) willReturn Future.successful(migrations)
      await(service.getState(0, 1, Seq.empty)) shouldBe migrations
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
    "Clear Back Ends" in {
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment())

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle FileStore Failure" in {
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment())

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

      await(service.resetEnvironment())

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

      await(service.resetEnvironment())

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

      await(service.resetEnvironment())

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

      await(service.resetEnvironment())

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
    val migratableCase = MigratableCase("1", CaseStatus.OPEN, Instant.EPOCH, 0, None, None, None, None, btiApplicationExample, None, Seq(migratableAttachment), Seq(migratableEvent1, migratableEvent2), Set("keyword1", "keyword2"))

    val attachment = Attachment(id = "name", public = true, timestamp = Instant.EPOCH)
    val aCase = Case("1", CaseStatus.OPEN, Instant.EPOCH, 0, None, None, None, None, btiApplicationExample, None, Seq(attachment), Set("keyword1", "keyword2"))

    val anUnprocessedMigration = Migration(migratableCase)

    "Migrate new Case with Attachments & Events" in {
      val aSuccessfullyPublishedFile = FileUploaded("id", "published", "text/plain", None, None)

      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
      givenCreatingAnEventReturnsItself()
      givenPublishingTheFileReturns(aSuccessfullyPublishedFile)
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      theCaseCreated shouldBe aCase
      theEventsCreated shouldBe Seq(migratableEvent1, migratableEvent2)
    }

    "Migrate new Case with Ruling Store failure" in {
      val aSuccessfullyPublishedFile = FileUploaded("id", "published", "text/plain", None, None)

      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
      givenCreatingAnEventReturnsItself()
      givenPublishingTheFileReturns(aSuccessfullyPublishedFile)
      givenNotifyingTheRulingStoreFails()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.PARTIAL_SUCCESS
      migrated.message shouldBe Seq("Failed to notify the ruling store [Notify Error]")

      theCaseCreated shouldBe aCase
      theEventsCreated shouldBe Seq(migratableEvent1, migratableEvent2)
    }

    "Migrate new Case with Attachments - with publish failure" in {
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
      givenCreatingAnEventReturnsItself()
      givenPublishingTheFileFails()
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.PARTIAL_SUCCESS
      migrated.message shouldBe Seq(
        "Failed to migrate 1/1 attachments",
        "Failed to migrate file [name] because [Publish Error]"
      )

      theCaseCreated shouldBe aCase
      theEventsCreated shouldBe Seq(migratableEvent1, migratableEvent2)
    }

    "Migrate existing Case with Attachments" in {
      val aSuccessfullyPublishedFile = FileUploaded("id", "published", "text/plain", None, None)

      givenTheCaseExistsWithoutAttachments()
      givenTheCaseExistsWithoutEvents()
      givenUpsertingTheCaseReturns(aCase)
      givenCreatingAnEventReturnsItself()
      givenPublishingTheFileReturns(aSuccessfullyPublishedFile)
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      theCaseCreated shouldBe aCase
      theEventsCreated shouldBe Seq(migratableEvent1, migratableEvent2)
    }

    "Migrate existing case with Attachments - removing missing attachments" in {
      val aSuccessfullyPublishedFile = FileUploaded("id", "published", "text/plain", None, None)

      givenTheCaseExistsWithAttachment("attachment-id")
      givenTheCaseExistsWithoutEvents()
      givenUpsertingTheCaseReturns(aCase)
      givenCreatingAnEventReturnsItself()
      givenPublishingTheFileReturns(aSuccessfullyPublishedFile)
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      verify(fileConnector).delete("attachment-id")
      theCaseCreated shouldBe aCase
      theEventsCreated shouldBe Seq(migratableEvent1, migratableEvent2)
    }

    "Migrate existing Case with existing Events" in {
      val aSuccessfullyPublishedFile = FileUploaded("id", "published", "text/plain", None, None)

      givenTheCaseExistsWithoutAttachments()
      givenTheCaseExistsWithEvents(migratableEvent1)
      givenUpsertingTheCaseReturns(aCase)
      givenCreatingAnEventReturnsItself()
      givenPublishingTheFileReturns(aSuccessfullyPublishedFile)
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      theCaseCreated shouldBe aCase
      theEventsCreated shouldBe Seq(migratableEvent2)
    }

    "Migrate new Case with failures" in {
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
      givenCreatingAnEventFails()
      givenPublishingTheFileFails()
      givenNotifyingTheRulingStoreFails()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.PARTIAL_SUCCESS
      migrated.message shouldBe Seq(
        "Failed to migrate 2/2 events",
        "Failed to migrate event [NOTE] because [Create Event Failure]",
        "Failed to migrate event [NOTE] because [Create Event Failure]",
        "Failed to notify the ruling store [Notify Error]",
        "Failed to migrate 1/1 attachments",
        "Failed to migrate file [name] because [Publish Error]"
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
      given(fileConnector.get(any[Seq[String]])(any[HeaderCarrier])) willReturn Future.successful(Seq(fileUploaded))
      given(fileConnector.delete(any[String])(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.getCase(any[String])(any[HeaderCarrier])) willReturn Future.successful(Some(aCase.copy(attachments = Seq(attachment))))
    }

    def givenPublishingTheFileReturns(aSuccessfullyUploadedFile: FileUploaded) = {
      given(fileConnector.publish(anyString())(any[HeaderCarrier])) willReturn Future.successful(aSuccessfullyUploadedFile)
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
