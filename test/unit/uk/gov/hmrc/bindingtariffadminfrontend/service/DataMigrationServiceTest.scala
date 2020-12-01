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
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileSearch, FileUploaded, UploadTemplate}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.{MigrationRepository, UploadRepository}
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.lock.LockRepository

import scala.collection.JavaConverters
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class DataMigrationServiceTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val migrationRepository    = mock[MigrationRepository]
  private val uploadRepository       = mock[UploadRepository]
  private val caseConnector          = mock[BindingTariffClassificationConnector]
  private val fileConnector          = mock[FileStoreConnector]
  private val rulingConnector        = mock[RulingConnector]
  private val upscanS3Connector      = mock[UpscanS3Connector]
  private val dataMigrationConnector = mock[DataMigrationJsonConnector]
  private val appConfig              = mock[AppConfig]
  private val lockRepository         = mock[LockRepository]

  private def migrationLock = new MigrationLock(lockRepository, appConfig)
  private def actorSystem   = ActorSystem.create("testActorSystem")

  private def withService(test: DataMigrationService => Any) =
    test(
      new DataMigrationService(
        migrationRepository    = migrationRepository,
        uploadRepository       = uploadRepository,
        migrationLock          = migrationLock,
        fileConnector          = fileConnector,
        upscanS3Connector      = upscanS3Connector,
        rulingConnector        = rulingConnector,
        caseConnector          = caseConnector,
        dataMigrationConnector = dataMigrationConnector,
        actorSystem            = actorSystem
      )
    )

  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(
      migrationRepository,
      uploadRepository,
      caseConnector,
      fileConnector,
      rulingConnector,
      upscanS3Connector,
      dataMigrationConnector,
      appConfig,
      lockRepository
    )
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    when(appConfig.dataMigrationLockLifetime) thenReturn FiniteDuration(60, TimeUnit.SECONDS)
    when(lockRepository.lock(anyString(), anyString(), any())) thenReturn Future.successful(true)
    when(lockRepository.releaseLock(anyString(), anyString())) thenReturn Future.successful(())
  }

  "Service 'getUploadedBatch'" should {
    "return the matching files" in withService { service =>
      val batch1 = List(
        FileUploaded(id = "id1", fileName = "name1", mimeType = "text/plain"),
        FileUploaded(id = "id2", fileName = "name2", mimeType = "text/plain")
      )

      givenBatchUploaded("batch1", batch1)

      val result = await(service.getUploadedBatch("batch1"))

      result shouldBe batch1
    }

    def givenBatchUploaded(batchId: String, files: List[FileUploaded]) = {
      given(uploadRepository.getByBatch(batchId)) willReturn Future.successful(
        files.map(file =>
          AttachmentUpload(fileName = file.fileName, mimeType = file.mimeType, id = file.id, batchId = batchId)
        )
      )
      files.map(file =>
        given(fileConnector.find(refEq(file.id))(any[HeaderCarrier])) willReturn Future.successful(Some(file))
      )
    }
  }

  "Service 'Counts'" should {

    "Delegate to Repository" in withService { service =>
      val counts = mock[MigrationCounts]
      given(migrationRepository.countByStatus) willReturn Future.successful(counts)
      await(service.counts) shouldBe counts
    }
  }

  //def migratedCaseCount(implicit hc: HeaderCarrier): Future[Int] =
  //    caseConnector.getCases(CaseSearch(migrated = Some(true)), Pagination(1, 1)).map(_.resultCount)
  //
  //  def totalCaseCount(implicit hc: HeaderCarrier): Future[Int] =
  //    caseConnector.getCases(CaseSearch(), Pagination(1, 1)).map(_.resultCount)

  "Service 'migratedCaseCount'" should {
    "Delegate to Repository" in withService { service =>
      val result = mock[Paged[Case]]
      given(result.resultCount) willReturn 35

      given(
        caseConnector.getCases(refEq(CaseSearch(migrated = Some(true))), refEq(Pagination(1, 1)))(any[HeaderCarrier])
      ) willReturn Future.successful(result)

      await(service.migratedCaseCount) shouldBe 35
    }
  }

  "Service 'totalCaseCount'" should {
    "Delegate to Repository" in withService { service =>
      val result = mock[Paged[Case]]
      given(result.resultCount) willReturn 35

      given(
        caseConnector.getCases(refEq(CaseSearch()), refEq(Pagination(1, 1)))(any[HeaderCarrier])
      ) willReturn Future.successful(result)

      await(service.totalCaseCount) shouldBe 35
    }
  }

  "Service 'Get State'" should {
    val migration  = mock[Migration]
    val migrations = Paged(Seq(migration))

    "Delegate to Repository" in withService { service =>
      given(migrationRepository.get(Seq.empty, Pagination())) willReturn Future.successful(migrations)
      await(service.getState(Seq.empty, Pagination())) shouldBe migrations
    }
  }

  "Service 'Get Next Unprocessed'" should {
    val migration = mock[Migration]

    "Delegate to Repository" in withService { service =>
      given(migrationRepository.get(MigrationStatus.UNPROCESSED)) willReturn Future.successful(Some(migration))
      await(service.getNextMigration) shouldBe Some(migration)
    }
  }

  "Service 'Prepare Migration'" should {
    val `case` = mock[MigratableCase]

    "Delegate to Repository" in withService { service =>
      given(migrationRepository.delete(any[Seq[Migration]])) willReturn Future.successful(true)
      given(migrationRepository.insert(any[Seq[Migration]])) willReturn Future.successful(true)

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
      verify(migrationRepository).insert(captor.capture())
      captor.getValue
    }

    def theMigrationsDeleted: Seq[Migration] = {
      val captor: ArgumentCaptor[Seq[Migration]] = ArgumentCaptor.forClass(classOf[Seq[Migration]])
      verify(migrationRepository).delete(captor.capture())
      captor.getValue
    }
  }

  "Service 'Update'" should {
    val migration        = mock[Migration]
    val migrationUpdated = mock[Migration]

    "Delegate to Repository" in withService { service =>
      given(migrationRepository.update(migration)) willReturn Future.successful(Some(migrationUpdated))

      await(service.update(migration)) shouldBe Some(migrationUpdated)
    }
  }

  "Service 'Process'" should {
    val extractDate          = Instant.ofEpochSecond(1609416000)
    val migratableAttachment = MigratedAttachment(public = true, name = "name", timestamp = Instant.EPOCH)
    val migratableEvent1     = MigratableEvent(details = Note("note"), operator = Operator("id"), timestamp = Instant.MAX)
    val migratableEvent2     = MigratableEvent(details = Note("other"), operator = Operator("id"), timestamp = Instant.MAX)
    val migratableCase = MigratableCase(
      "1",
      CaseStatus.OPEN,
      Instant.EPOCH,
      0,
      Some(0),
      None,
      None,
      None,
      None,
      btiApplicationExample,
      None,
      Seq.empty,
      Seq.empty,
      Set("keyword1", "keyword2"),
      dateOfExtract = Some(extractDate)
    )
    val migratableCaseWithEvents = MigratableCase(
      "1",
      CaseStatus.OPEN,
      Instant.EPOCH,
      0,
      Some(0),
      None,
      None,
      None,
      None,
      btiApplicationExample,
      None,
      Seq.empty,
      Seq(migratableEvent1, migratableEvent2),
      Set("keyword1", "keyword2"),
      dateOfExtract = Some(extractDate)
    )
    val migratableCaseWithAttachments = MigratableCase(
      "1",
      CaseStatus.OPEN,
      Instant.EPOCH,
      0,
      Some(0),
      None,
      None,
      None,
      None,
      btiApplicationExample,
      None,
      Seq(migratableAttachment),
      Seq.empty,
      Set("keyword1", "keyword2"),
      dateOfExtract = Some(extractDate)
    )

    val attachment = Attachment(id = "id", public = true, timestamp = Instant.EPOCH)
    val aCase = Case(
      "1",
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
      "1",
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
      Seq(attachment),
      Set("keyword1", "keyword2"),
      dateOfExtract = Some(extractDate)
    )

    val anUnprocessedMigration                = Migration(migratableCase)
    val anUnprocessedHistoricMigration        = Migration(migratableCase.copy(dateOfExtract = Some(Instant.EPOCH)))
    val anUnprocessedMigrationWithAttachments = Migration(migratableCaseWithAttachments)
    val anUnprocessedMigrationWithEvents      = Migration(migratableCaseWithEvents)

    "Migrate new Case" in withService { service =>
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturnsItself()
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status  shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      theCaseCreated shouldBe aCase
    }

    "Migrate new Case - with Ruling Store failure" in withService { service =>
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseReturnsItself()
      givenNotifyingTheRulingStoreFails()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status  shouldBe MigrationStatus.PARTIAL_SUCCESS
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
      val aSuccessfullyUploadedFile = FileUploaded(id = "id", fileName = "name", mimeType = "text/plain")

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

      theCaseCreated shouldBe aCase
    }

    "Migrate new Case with Attachments - with pre-published file" in withService { service =>
      val aSuccessfullyUploadedFile = FileUploaded(
        id         = "id",
        fileName   = "name",
        mimeType   = "text/plain",
        url        = None,
        scanStatus = None,
        published  = true
      )

      givenTheCaseDoesNotAlreadyExist()
      givenRetrievingTheUploadedFilesReturns(aSuccessfullyUploadedFile)
      givenUpsertingTheCaseReturnsItself()
      givenNotifyingTheRulingStoreSucceeds()

      val migrated = await(service.process(anUnprocessedMigrationWithAttachments))
      migrated.status  shouldBe MigrationStatus.SUCCESS
      migrated.message shouldBe Seq.empty

      theCaseCreated shouldBe aCaseWithAttachments

      verify(fileConnector, never()).publish(any[String])(any[HeaderCarrier])
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

    "Skip existing Case from newer extract - with aborted status" in withService { service =>
      givenTheCaseExistsFromHistoricExtract()

      val migrated = await(
        service.process(
          anUnprocessedMigration.copy(`case` = anUnprocessedMigration.`case`.copy(status = CaseStatus.COMPLETED))
        )
      )
      migrated.status shouldBe MigrationStatus.ABORTED
      migrated.message shouldBe Seq(
        "An earlier extract containing this case has already been uploaded",
        "Previously migrated from the 1970-01-01 extracts",
        "Newer information from this 2020-12-31 extract may be lost",
        "Status from migration [COMPLETED] is different to the existing case [OPEN]"
      )

      verifyNoCaseCreated
      verifyNoEventsCreated
    }

    "Skip existing Case from same extract - with skipped status" in withService { service =>
      givenTheCaseExists()

      val migrated = await(service.process(anUnprocessedMigration))
      migrated.status shouldBe MigrationStatus.SKIPPED
      migrated.message shouldBe Seq(
        "The extract containing this case has already been uploaded"
      )

      verifyNoCaseCreated
      verifyNoEventsCreated
    }

    "Skip existing Case from earlier extract - with skipped status" in withService { service =>
      givenTheCaseExists()

      val migrated = await(service.process(anUnprocessedHistoricMigration))
      migrated.status shouldBe MigrationStatus.SKIPPED
      migrated.message shouldBe Seq(
        "A newer extract containing this case has already been uploaded",
        "Previously migrated from the 2020-12-31 extracts"
      )

      verifyNoCaseCreated
      verifyNoEventsCreated
    }

    "Throw Exception on Upsert Failure" in withService { service =>
      givenTheCaseDoesNotAlreadyExist()
      givenUpsertingTheCaseFails()

      intercept[RuntimeException] {
        await(service.process(anUnprocessedMigration))
      }.getMessage shouldBe "Upsert Error"
    }

    def givenTheCaseExists(): Unit =
      given(caseConnector.getCase(any[String])(any[HeaderCarrier])) willReturn Future.successful(Some(aCase))

    def givenTheCaseExistsFromHistoricExtract(): Unit =
      given(caseConnector.getCase(any[String])(any[HeaderCarrier])) willReturn Future.successful(
        Some(aCase.copy(dateOfExtract = Some(Instant.EPOCH)))
      )

    def givenCreatingAnEventFails(): Unit =
      given(caseConnector.createEvent(anyString(), any[Event])(any[HeaderCarrier])) willReturn Future.failed(
        new RuntimeException("Create Event Failure")
      )

    def givenRetrievingTheUploadedFilesFails(): Unit = {
      given(uploadRepository.getByFileNames(any[List[String]])) willReturn Future.successful(
        Nil
      )
      given(fileConnector.find(any[FileSearch], any[Pagination])(any[HeaderCarrier])) willReturn Future.failed(
        new RuntimeException("Find Error")
      )
    }

    def givenRetrievingTheUploadedFilesReturnsNone(): Unit = {
      given(uploadRepository.getByFileNames(any[List[String]])) willReturn Future.successful(
        Nil
      )

      given(fileConnector.find(any[FileSearch], any[Pagination])(any[HeaderCarrier])) willReturn Future.successful(
        Paged.empty[FileUploaded]
      )
    }

    def givenRetrievingTheUploadedFilesReturns(files: FileUploaded*): Unit = {
      given(uploadRepository.getByFileNames(any[List[String]])) willReturn Future.successful(
        files.toList.map(file => AttachmentUpload(file.fileName, file.mimeType, file.id, "batchId"))
      )
      given(fileConnector.find(any[FileSearch], any[Pagination])(any[HeaderCarrier])) willReturn Future.successful(
        Paged(files.toSeq)
      )
    }

    def givenPublishingTheFileFails(): Unit =
      given(fileConnector.publish(anyString())(any[HeaderCarrier])) willReturn Future.failed(
        new RuntimeException("Publish Error")
      )

    def givenNotifyingTheRulingStoreFails() =
      given(rulingConnector.notify(anyString())(any[HeaderCarrier])) willReturn Future.failed(
        new RuntimeException("Notify Error")
      )

    def givenNotifyingTheRulingStoreSucceeds() =
      given(rulingConnector.notify(anyString())(any[HeaderCarrier])) willReturn Future.successful(())

    def givenUpsertingTheCaseReturnsItself(): Unit =
      given(caseConnector.upsertCase(any[Case])(any[HeaderCarrier])) willAnswer new Answer[Future[Case]] {
        override def answer(invocation: InvocationOnMock): Future[Case] = Future.successful(invocation.getArgument(0))
      }

    def givenUpsertingTheCaseFails() =
      given(caseConnector.upsertCase(any[Case])(any[HeaderCarrier])) willReturn Future.failed(
        new RuntimeException("Upsert Error")
      )

    def givenTheCaseDoesNotAlreadyExist() = {
      given(caseConnector.getCase(any[String])(any[HeaderCarrier])) willReturn Future.successful(None)
      given(caseConnector.getEvents(any[String], any[Pagination])(any[HeaderCarrier])) willReturn Future.failed(
        new NotFoundException("events not found")
      )
    }

    def theCaseCreated: Case = {
      val captor: ArgumentCaptor[Case] = ArgumentCaptor.forClass(classOf[Case])
      verify(caseConnector).upsertCase(captor.capture())(any())
      captor.getValue
    }

    def verifyNoCaseCreated =
      verify(caseConnector, never()).upsertCase(any[Case])(any[HeaderCarrier])

    def verifyNoEventsCreated =
      verify(caseConnector, never()).createEvent(anyString(), any[Event])(any[HeaderCarrier])

    implicit def list2seq[T]: java.util.List[T] => Seq[T] = JavaConverters.asScalaBufferConverter(_).asScala.toSeq

  }

  "Service clear" should {
    "Delegate to repository" in withService { service =>
      given(migrationRepository.delete(None)) willReturn Future.successful(true)
      await(service.clear()) shouldBe true
    }

    "Delegate to repository with status" in withService { service =>
      given(migrationRepository.delete(Some(MigrationStatus.SUCCESS))) willReturn Future.successful(true)
      await(service.clear(Some(MigrationStatus.SUCCESS))) shouldBe true
    }
  }

  "Service upload" should {
    "Delegate to connector" in withService { service =>
      val request  = AttachmentUpload("name", "type", "id", "batchId")
      val template = UploadTemplate("href", Map())
      val file     = mock[TemporaryFile]

      given(uploadRepository.update(request)) willReturn Future.successful(Some(request))
      given(fileConnector.initiate(request)) willReturn Future.successful(template)
      given(upscanS3Connector.upload(template, file, request)) willReturn Future.successful(())

      await(service.upload(request, file))

      verify(uploadRepository).update(request)
      verify(fileConnector).initiate(request)
      verify(upscanS3Connector).upload(template, file, request)

    }
  }

}
