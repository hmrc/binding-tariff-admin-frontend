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

import javax.inject.Inject
import play.api.Logger
import play.api.libs.Files.TemporaryFile
import uk.gov.hmrc.bindingtariffadminfrontend.connector.{BindingTariffClassificationConnector, FileStoreConnector, RulingConnector, UpscanS3Connector}
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigrationStatus.MigrationStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model.Store.Store
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.{Case, Event}
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileSearch, UploadRequest, UploadTemplate}
import uk.gov.hmrc.bindingtariffadminfrontend.model.{MigrationStatus, _}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.MigrationRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{sequence, successful}
import scala.util.Success

class DataMigrationService @Inject()(repository: MigrationRepository,
                                     fileConnector: FileStoreConnector,
                                     upscanS3Connector: UpscanS3Connector,
                                     rulingConnector: RulingConnector,
                                     caseConnector: BindingTariffClassificationConnector) {

  def getState(status: Seq[MigrationStatus], pagination: Pagination): Future[Paged[Migration]] = {
    repository.get(status, pagination)
  }

  def counts: Future[MigrationCounts] = {
    repository.countByStatus
  }

  def prepareMigration(cases: Seq[MigratableCase]): Future[Boolean] = {
    val migrations = cases.map(Migration(_))
    for {
      _ <- repository.delete(migrations)
      result <- repository.insert(migrations)
    } yield result
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
      updated <- deleteMissingAttachments(existingCase, migration)

      // Create or Update The Case
      _ <- caseConnector.upsertCase(migration.`case`.toCase)

      // Filter out any events that already exist on the case
      updated <- filterOutExistingEvents(existingCase, updated)

      // Create the events
      updated <- createEvents(updated)

      //Publish The Files
      updated <- publishUploads(updated)

      // Notify The Ruling Store
      updated <- notifyRulingStore(updated)

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
        caseConnector.createEvent(migration.`case`.reference, event).map(MigrationSuccess(_)) recover withFailure(event)
      }
    ) map {
      case migrations: Seq[MigrationState[Event]] if migrations.exists(_.isFailure) =>
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

  private def publishUploads(migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] = {
    sequence(
      migration.`case`.attachments.map { a =>
        fileConnector.find(a.id) flatMap {
          case Some(file) if file.published => Future.successful(MigrationSuccess(a))
          case Some(_) => fileConnector.publish(a.id).map(_ => MigrationSuccess(a))
          case None => Future.successful(MigrationFailure(a, MigrationFailedException("Not found")))
        } recover withFailure(a)
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

  private def deleteMissingAttachments(existingCase: Option[Case], migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] = existingCase match {
    case None => successful(migration)
    case Some(c) =>
      val search = FileSearch(ids = Some(c.attachments.map(_.id).toSet))
      for {
        existingFileIds: Seq[String] <- if (c.attachments.nonEmpty) fileConnector.find(search, Pagination.max).map(_.results.map(_.id)) else successful(Seq.empty)
        newFileIds: Seq[String] = migration.`case`.attachments.map(_.id)
        deletedFileIds: Seq[String] = existingFileIds.filterNot(f => newFileIds.contains(f))
        _ <- sequence(deletedFileIds.map(f => fileConnector.delete(f)))
      } yield migration
  }


  private def filterOutExistingEvents(existingCase: Option[Case], migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] = existingCase match {
    case Some(c) =>
      caseConnector.getEvents(c.reference) recover withResponse(Seq.empty[Event]) map { existingEvents: Seq[Event] =>
        val updatedEvents: Seq[Event] = migration.`case`.events
        val newEvents = updatedEvents.filterNot(existingEvents.contains)
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
