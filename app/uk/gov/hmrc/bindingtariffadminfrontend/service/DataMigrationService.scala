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

import javax.inject.Inject
import play.api.Logger
import play.api.libs.Files.TemporaryFile
import uk.gov.hmrc.bindingtariffadminfrontend.connector.{BindingTariffClassificationConnector, FileStoreConnector, RulingConnector, UpscanS3Connector}
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigrationStatus.MigrationStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model.Store.Store
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.{Case, Event}
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileSearch, FileUploaded, UploadRequest, UploadTemplate}
import uk.gov.hmrc.bindingtariffadminfrontend.model.{MigrationStatus, _}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.MigrationRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{sequence, successful}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Success

class DataMigrationService @Inject()(repository: MigrationRepository,
                                     fileConnector: FileStoreConnector,
                                     upscanS3Connector: UpscanS3Connector,
                                     rulingConnector: RulingConnector,
                                     caseConnector: BindingTariffClassificationConnector) {

  def getDataMigrationFilesDetails(fileNames:List[String])(implicit hc: HeaderCarrier): Future[List[FileUploaded]] ={

    val opts =  fileNames.map{ file =>
      fileConnector.find(file).map{
        case Some(data) =>  data
        case None =>  throw new RuntimeException("No data found")
      }
    }

    Future.sequence(opts)
  }

  def getState(status: Seq[MigrationStatus], pagination: Pagination): Future[Paged[Migration]] = {
    repository.get(status, pagination)
  }

  def counts: Future[MigrationCounts] = {
    repository.countByStatus
  }

  def prepareMigration(cases: Seq[MigratableCase], priority: Boolean = false)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val migrations = cases.map(Migration(_))
    val groupSize = 5000
    val grouped: immutable.Seq[Seq[Migration]] = migrations.grouped(groupSize).toList

    for (group <- grouped) {
      val delete = repository.delete(group)
      Await.result(delete, 30.seconds)

      val innerInsert = repository.insert(group)
      Await.result(innerInsert, 30.seconds)

      if (priority) {
        val future = Future.sequence(group.map(process(_).flatMap(update)))
        Await.result(future, 30.seconds)
      }
    }

    Future.successful(true)
  }

  def getNextMigration: Future[Option[Migration]] = {
    repository.get(MigrationStatus.UNPROCESSED)
  }

  def update(migration: Migration): Future[Option[Migration]] = {
    repository.update(migration)
  }

  def clear(status: Option[MigrationStatus] = None): Future[Boolean] = {
    repository.delete(status)
  }

  def resetEnvironment(stores: Set[Store])(implicit hc: HeaderCarrier): Future[Unit] = {

    def resetIfPresent(store: Store, expression: => Future[Any]): Future[Unit] = if (stores.contains(store)) {
      expression.map(_ => ()) recover loggingAWarning
    } else Future.successful(())

    def loggingAWarning: PartialFunction[Throwable, Unit] = {
      case t: Throwable => Logger.warn("Failed to clear Service", t)
    }

    for {
      _ <- resetIfPresent(Store.FILES, fileConnector.delete())
      _ <- resetIfPresent(Store.CASES, caseConnector.deleteCases())
      _ <- resetIfPresent(Store.EVENTS, caseConnector.deleteEvents())
      _ <- resetIfPresent(Store.RULINGS, rulingConnector.delete())
      _ <- resetIfPresent(Store.MIGRATION, clear())
    } yield ()
  }

  def initiateFileMigration(upload: UploadRequest)(implicit hc: HeaderCarrier): Future[UploadTemplate] = {
    fileConnector.initiate(upload)
  }

  def upload(upload: UploadRequest, file: TemporaryFile)(implicit hc: HeaderCarrier): Future[Unit] = {
    for {
      template <- fileConnector.initiate(upload)
      _ <- upscanS3Connector.upload(template, file)
    } yield ()
  }

  def process(migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] = {
    Logger.info(s"Case Migration with reference [${migration.`case`.reference}]: Starting")

    for {
      existingCase <- caseConnector.getCase(migration.`case`.reference)

      // Delete any existing attachments that aren't in the migration
      _ <- deleteExistingAttachmentsNotOnTheMigration(existingCase, migration)

      // Find the files for this Migration
      migratedFiles: Seq[FileUploaded] <- findMigratedFiles(migration)

      // Filter any un-migrated files from the migration & add a warning
      updated: Migration = filterUnMigratedAttachmentsFromTheMigration(migratedFiles, migration)

      // Create or Update The Case
      _ <- caseConnector.upsertCase(updated.`case`.toCase)

      // Filter out any events that already exist on the case
      updated: Migration <- filterOutExistingEvents(existingCase, updated)

      // Create the events
      updated: Migration <- createEvents(updated)

      //Publish The Files
      updated: Migration <- publishUploads(migratedFiles, updated)

      // Notify The Ruling Store
      updated: Migration <- notifyRulingStore(updated)

      status = if (updated.status == MigrationStatus.UNPROCESSED) MigrationStatus.SUCCESS else updated.status

    } yield updated.copy(status = status)
  }

  private def notifyRulingStore(migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] = {
    rulingConnector
      .notify(migration.`case`.reference)
      .map(Success(_))
      .recover(withFailure(()))
      .map {
        case MigrationFailure(_, t: Throwable) =>
          migration
            .copy(status = MigrationStatus.PARTIAL_SUCCESS)
            .appendMessage(s"Failed to notify the ruling store [${t.getMessage}]")
        case _ =>
          migration
      }
  }

  private def createEvents(migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] = {
    sequence(
      migration.`case`.events map { event =>
        caseConnector.createEvent(migration.`case`.reference, event.toEvent(migration.`case`.reference)).map(_ => MigrationSuccess(event)) recover withFailure(event)
      }
    ) map {
      case migrations: Seq[MigrationState[MigratableEvent]] if migrations.exists(_.isFailure) =>
        val failedMigrations = migrations.filter(_.isFailure).map(_.asFailure)
        val summaryMessage = s"Failed to migrate ${failedMigrations.size}/${migrations.size} events"
        val failureMessages = failedMigrations.map(f => s"Failed to migrate event [${f.subject.details.`type`}] because [${f.cause.getMessage}]")

        migration
          .copy(status = MigrationStatus.PARTIAL_SUCCESS)
          .appendMessage(summaryMessage)
          .appendMessage(failureMessages)
      case _ =>
        migration
    }
  }

  private def publishUploads(migratedFiles: Seq[FileUploaded], migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] = {
    val filesById: Map[String, FileUploaded] = migratedFiles.map(f => f.id -> f).toMap

    sequence(
      migration.`case`.attachments.map { a =>
        filesById.get(a.id) match {
          case Some(file) if file.published => Future.successful(MigrationSuccess(a))
          case Some(_) => fileConnector.publish(a.id).map(_ => MigrationSuccess(a)) recover withFailure(a)
          case None => Future.successful(MigrationFailure(a, MigrationFailedException("Not found")))
        }
      }
    ) map {
      case migrations: Seq[MigrationState[MigratedAttachment]] if migrations.exists(_.isFailure) =>
        val failedMigrations = migrations.filter(_.isFailure).map(_.asFailure)
        val summaryMessage = s"Failed to migrate ${failedMigrations.size}/${migrations.size} attachments"
        val failureMessages = failedMigrations.map(f => s"Failed to migrate file [${f.subject.name}] because [${f.cause.getMessage}]")

        migration
          .copy(status = MigrationStatus.PARTIAL_SUCCESS)
          .appendMessage(summaryMessage)
          .appendMessage(failureMessages)
      case _ =>
        migration
    }
  }

  def findMigratedFiles(migration: Migration)(implicit hc: HeaderCarrier): Future[Seq[FileUploaded]] = {
    val newFiles = migration.`case`.attachments
    val newFileIds = newFiles.map(_.id).toSet
    if (newFileIds.nonEmpty) {
      fileConnector.find(FileSearch(ids = Some(newFileIds)), Pagination.max).map(_.results) recover withResponse(Seq.empty)
    } else successful(Seq.empty)
  }

  private def filterUnMigratedAttachmentsFromTheMigration(filesMigrated: Seq[FileUploaded], migration: Migration): Migration = {
    val newFiles = migration.`case`.attachments
    val newFileIds = newFiles.map(_.id).toSet
    val newFileIdsFound: Seq[String] = filesMigrated.map(_.id)
    val missingMigratableFiles: Seq[MigratedAttachment] = newFiles.filterNot(att => newFileIdsFound.contains(att.id))
    missingMigratableFiles match {
      case missing if missing.nonEmpty =>
        val summaryMessage = s"Failed to migrate ${missing.size}/${newFileIds.size} attachments"
        val failureMessages = missing.map(f => s"Failed to migrate file [${f.name}] because [Not Found]")
        val `case` = migration.`case`.copy(attachments = newFiles.filter(f => newFileIdsFound.contains(f.id)))
        migration
          .copy(status = MigrationStatus.PARTIAL_SUCCESS, `case` = `case`)
          .appendMessage(summaryMessage)
          .appendMessage(failureMessages)
      case _ =>
        migration
    }
  }

  private def deleteExistingAttachmentsNotOnTheMigration(existingCase: Option[Case], migration: Migration)
                                                        (implicit hc: HeaderCarrier): Future[Unit] = existingCase match {
    case None =>
      successful(())

    case Some(c) =>
      val existingFiles = c.attachments
      val existingFileIds = existingFiles.map(_.id).toSet
      val newFiles = migration.`case`.attachments
      val newFileIds = newFiles.map(_.id).toSet
      for {
        // Get Files already on the case
        existingFilesFound: Seq[FileUploaded] <-
          if (existingFileIds.nonEmpty) {
            fileConnector.find(FileSearch(ids = Some(existingFileIds)), Pagination.max).map(_.results)
          } else successful(Seq.empty)
        existingFileIdsFound: Seq[String] = existingFilesFound.map(_.id)

        // Delete Files which are on the case already, but not part of the migration
        deletedFileIds: Seq[String] = existingFileIdsFound.filterNot(f => newFileIds.contains(f))
        _ <- sequence(deletedFileIds.map(f => fileConnector.delete(f)))
      } yield ()
  }

  private def filterOutExistingEvents(existingCase: Option[Case], migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] = existingCase match {
    case Some(c) =>
      caseConnector.getEvents(c.reference, Pagination.max).map(_.results) recover withResponse(Seq.empty[Event]) map { existingEvents: Seq[Event] =>
        val updatedEvents: Seq[MigratableEvent] = migration.`case`.events
        val newEvents = updatedEvents.filterNot(me => existingEvents.contains(me.toEvent(migration.`case`.reference)))
        val updatedCase = migration.`case`.copy(events = newEvents)
        migration.copy(updatedCase)
      }
    case None => Future.successful(migration)
  }

  private def withFailure[T](subject: T, mapping: Throwable => Throwable = t => t): PartialFunction[Throwable, MigrationState[T]] = {
    case t: Throwable => MigrationFailure(subject, mapping(t))
  }

  private def withResponse[T](response: T): PartialFunction[Throwable, T] = {
    case _ => response
  }

}
