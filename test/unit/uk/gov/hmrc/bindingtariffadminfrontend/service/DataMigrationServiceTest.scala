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

import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffadminfrontend.connector.{BindingTariffClassificationConnector, FileStoreConnector}
import uk.gov.hmrc.bindingtariffadminfrontend.model.Cases.btiApplicationExample
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.{Attachment, Case, CaseStatus}
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.FileUploaded
import uk.gov.hmrc.bindingtariffadminfrontend.repository.MigrationRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class DataMigrationServiceTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val repository = mock[MigrationRepository]
  private val caseConnector = mock[BindingTariffClassificationConnector]
  private val fileConnector = mock[FileStoreConnector]
  private val service = new DataMigrationService(repository, fileConnector, caseConnector)
  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.reset(repository, caseConnector, fileConnector)
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
      given(repository.get()) willReturn Future.successful(migrations)
      await(service.getState) shouldBe migrations
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
        Migration(`case`, MigrationStatus.UNPROCESSED, None)
      )

      theMigrationsDeleted shouldBe Seq(
        Migration(`case`, MigrationStatus.UNPROCESSED, None)
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
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment())

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle FileStore Failure" in {
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment())

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle CaseStore Case Delete Failure " in {
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment())

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle CaseStore Event Delete Failure" in {
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(repository.delete(None)) willReturn Future.successful(true)

      await(service.resetEnvironment())

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(repository).delete(None)
    }

    "Handle Migrations Delete Failure" in {
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(repository.delete(None)) willReturn Future.failed(new RuntimeException("Error"))

      await(service.resetEnvironment())

      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(repository).delete(None)
    }
  }

  "Service 'Process'" should {
    val migratableAttachment = MigratableAttachment(public = true, url = "url", name = "name", mimeType = "text/plain", timestamp = Instant.EPOCH)
    val migratableCase = MigratableCase("1", CaseStatus.OPEN, Instant.EPOCH, 0, None, None, None, None, btiApplicationExample, None, Seq(migratableAttachment), Set("keyword1", "keyword2"))

    val attachment = Attachment("id", public = true, Instant.EPOCH)
    val aCase = Case("1", CaseStatus.OPEN, Instant.EPOCH, 0, None, None, None, None, btiApplicationExample, None, Seq(attachment), Set("keyword1", "keyword2"))

    val anUnprocessedMigration = Migration(migratableCase)

    "Migrate all Attachments and Case" in {
      val aSuccessfullyUploadedFile = FileUploaded("id", "uploaded", "text/plain", None, None)
      val aSuccessfullyPublishedFile = FileUploaded("id", "published", "text/plain", None, None)

      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
      givenUploadingTheFileReturns(aSuccessfullyUploadedFile)
      givenPublishingTheFileReturns(aSuccessfullyPublishedFile)

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe None

      theCaseCreated shouldBe aCase
    }

    "Migrate some Attachments and Case when upload fails" in {

      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
      givenUploadingTheFileFails()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.PARTIAL_SUCCESS
      migrated.message shouldBe Some("1/1 Attachments Failed [url]")

      theCaseCreated.attachments shouldBe Seq.empty
    }

    "Migrate some Attachments and Case when publish fails" in {
      val aSuccessfullyUploadedFile = FileUploaded("id", "uploaded", "text/plain", None, None)

      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
      givenUploadingTheFileReturns(aSuccessfullyUploadedFile)
      givenPublishingTheFileFails()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.PARTIAL_SUCCESS
      migrated.message shouldBe Some("1/1 Attachments Failed [url]")

      theCaseCreated shouldBe aCase
    }

    "Migrate all Attachments for existing Case" in {
      val aSuccessfullyUploadedFile = FileUploaded("id", "uploaded", "text/plain", None, None)
      val aSuccessfullyPublishedFile = FileUploaded("id", "published", "text/plain", None, None)

      givenTheCaseExistsWithoutAttachments()
      givenUpsertingTheCaseReturns(aCase)
      givenUploadingTheFileReturns(aSuccessfullyUploadedFile)
      givenPublishingTheFileReturns(aSuccessfullyPublishedFile)

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe None

      theCaseCreated shouldBe aCase
    }

    "Migrate all attachments for existing Case (with attachments)" in {
      val aSuccessfullyUploadedFile = FileUploaded("id", "name", "text/plain", None, None)
      val aSuccessfullyPublishedFile = FileUploaded("id", "published", "text/plain", None, None)

      givenTheCaseExistsWithAttachment("attachment-id")
      givenUpsertingTheCaseReturns(aCase)
      givenUploadingTheFileReturns(aSuccessfullyUploadedFile)
      givenPublishingTheFileReturns(aSuccessfullyPublishedFile)

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe None

      verify(fileConnector).delete("attachment-id")
      theCaseCreated shouldBe aCase
    }

    "Throw Exception on Upsert Failure" in {
      val aSuccessfullyUploadedFile = FileUploaded("id", "name", "text/plain", None, None)

      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturns(aCase)
      givenUploadingTheFileReturns(aSuccessfullyUploadedFile)
      givenUpsertingTheCaseFails()

      intercept[RuntimeException] {
        await(service.process(anUnprocessedMigration))
      }.getMessage shouldBe "Upsert Error"
    }

    def givenTheCaseExistsWithoutAttachments(): Unit = {
      given(fileConnector.delete(any[String])(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.getCase(any[String])(any[HeaderCarrier])) willReturn Future.successful(Some(aCase))
    }

    def givenTheCaseExistsWithAttachment(id: String): Unit = {
      val attachment = mock[Attachment]
      given(attachment.id) willReturn id
      given(fileConnector.delete(any[String])(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.getCase(any[String])(any[HeaderCarrier])) willReturn Future.successful(Some(aCase.copy(attachments = Seq(attachment))))
    }

    def givenUploadingTheFileReturns(aSuccessfullyUploadedFile: FileUploaded) = {
      given(fileConnector.upload(any[MigratableAttachment])) willReturn Future.successful(aSuccessfullyUploadedFile)
    }

    def givenUploadingTheFileFails() = {
      given(fileConnector.upload(any[MigratableAttachment])) willReturn Future.failed(new RuntimeException("Upload Error"))
    }

    def givenPublishingTheFileReturns(aSuccessfullyUploadedFile: FileUploaded) = {
      given(fileConnector.publish(anyString())(any[HeaderCarrier])) willReturn Future.successful(aSuccessfullyUploadedFile)
    }

    def givenPublishingTheFileFails() = {
      given(fileConnector.publish(anyString())(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Publish Error"))
    }

    def givenUpsertingTheCaseReturns(aCase: Case) = {
      given(caseConnector.upsertCase(any[Case])(any[HeaderCarrier])) willReturn Future.successful(aCase)
    }

    def givenUpsertingTheCaseFails() = {
      given(caseConnector.upsertCase(any[Case])(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Upsert Error"))
    }

    def givenTheCaseDoesNotAlreadyExist() = {
      given(caseConnector.getCase(any[String])(any[HeaderCarrier])) willReturn Future.successful(None)
    }

    def theCaseCreated: Case = {
      val captor: ArgumentCaptor[Case] = ArgumentCaptor.forClass(classOf[Case])
      verify(caseConnector).upsertCase(captor.capture())(any())
      captor.getValue
    }

  }

  "Service clear" should {
    "Delegate to repository" in {
      given(service.clear()) willReturn Future.successful(true)
      await(service.clear()) shouldBe true
    }

    "Delegate to repository with status" in {
      given(service.clear(Some(MigrationStatus.SUCCESS))) willReturn Future.successful(true)
      await(service.clear(Some(MigrationStatus.SUCCESS))) shouldBe true
    }
  }

}
