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
import uk.gov.hmrc.bindingtariffadminfrontend.model.{MigrationStatus, _}
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.Event
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileUploaded, UploadRequest, UploadTemplate}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.MigrationRepository
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{sequence, successful}
import scala.util.{Failure, Success, Try}

class DataMigrationService @Inject()(repository: MigrationRepository,
                                     fileConnector: FileStoreConnector,
                                     upscanS3Connector: UpscanS3Connector,
                                     rulingConnector: RulingConnector,
                                     caseConnector: BindingTariffClassificationConnector) {

  def getState(page: Int, pageSize: Int, status: Seq[MigrationStatus]): Future[Seq[Migration]] = {
    repository.get(page, pageSize, status)
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

  def resetEnvironment()(implicit hc: HeaderCarrier): Future[Unit] = {

    def loggingAWarning: PartialFunction[Throwable, Unit] = {
      case t: Throwable => Logger.warn("Failed to clear Service", t)
    }

    for {
      _ <- fileConnector.delete() recover loggingAWarning
      _ <- caseConnector.deleteCases() recover loggingAWarning
      _ <- caseConnector.deleteEvents() recover loggingAWarning
      _ <- rulingConnector.delete() recover loggingAWarning
      _ <- clear() recover loggingAWarning
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
      // Delete any existing attachments that aren't in the migration
      _ <- deleteMissingAttachments(migration)

      // Create or Update The Case
      _ <- caseConnector.upsertCase(migration.`case`.toCase)

      // Filter out any events that already exist on the case
      updated <- filterOutExistingEvents(migration)

      // Create the events
      updated <- createEvents(updated)

      // Notify The Ruling Store
      updated <- notifyRulingStore(updated)

      //Publish The Files
      updated <- publishUploads(updated)

      status = if(updated.message.isEmpty) MigrationStatus.SUCCESS else MigrationStatus.PARTIAL_SUCCESS

    } yield updated.copy(status = status)
  }

  private def notifyRulingStore(migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] = {
    rulingConnector
      .notify(migration.`case`.reference)
      .map(Success(_))
      .recover(withFailure(()))
      .map {
        case MigrationFailure(_, t: Throwable) => migration.copy(message = migration.message :+ s"Failed to notify the ruling store [${t.getMessage}]")
        case _ => migration
      }
  }

  private def createEvents(migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] = {
    sequence(
      migration.`case`.events map { event =>
        caseConnector.createEvent(migration.`case`.reference, event).map(MigrationSuccess(_)) recover withFailure(event)
      }
    ) map {
      case migrations: Seq[MigrationState[Event]] if migrations.exists(_.isFailure) =>
        val messages = migration.message
        val failedMigrations = migrations.filter(_.isFailure).map(_.asFailure)
        val summaryMessage = s"Failed to migrate ${failedMigrations.size}/${migrations.size} events"
        val failureMessages = failedMigrations.map (f => s"Failed to migrate event [${f.subject.details.`type`}] because [${f.cause.getMessage}]")

        migration.copy(message = (messages :+ summaryMessage) ++ failureMessages)
      case _ =>
        migration
    }
  }

  private def publishUploads(migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] = {
    sequence(
      migration.`case`.attachments.map { a =>
        fileConnector.publish(a.id).map(_ => MigrationSuccess(a)) recover withFailure(a, {
          case _: NotFoundException => new MigrationFailedException("Not found")
          case t => t
        })
      }
    ) map {
      case migrations: Seq[MigrationState[MigratedAttachment]] if migrations.exists(_.isFailure) =>
        val messages = migration.message
        val failedMigrations = migrations.filter(_.isFailure).map(_.asFailure)
        val summaryMessage = s"Failed to migrate ${failedMigrations.size}/${migrations.size} attachments"
        val failureMessages = failedMigrations.map (f => s"Failed to migrate file [${f.subject.name}] because [${f.cause.getMessage}]")

        migration.copy(message = (messages :+ summaryMessage) ++ failureMessages)
      case _ =>
        migration
    }
  }

  private def deleteMissingAttachments(migration: Migration)(implicit hc: HeaderCarrier): Future[Unit] = {
    caseConnector.getCase(migration.`case`.reference) flatMap {
      case None => successful(())
      case Some(c) =>
        for {
          files <- if (c.attachments.nonEmpty) fileConnector.get(c.attachments.map(_.id)) else successful(Seq.empty)
          newAttachmentIDs = migration.`case`.attachments.map(_.id)
          missingFiles: Seq[FileUploaded] = files.filterNot(f => newAttachmentIDs.contains(f.id))
          _ <- sequence(missingFiles.map(f => fileConnector.delete(f.id)))
        } yield ()
    }
  }

  private def filterOutExistingEvents(migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] = {
    caseConnector.getEvents(migration.`case`.reference) recover withResponse(Seq.empty[Event]) map { existingEvents: Seq[Event] =>
      val updatedEvents: Seq[Event] = migration.`case`.events
      val newEvents = updatedEvents.filterNot(existingEvents.contains)
      val updatedCase = migration.`case`.copy(events = newEvents)
      migration.copy(updatedCase)
    }
  }

  private def withFailure[T](subject: T, mapping: Throwable => Throwable = t => t): PartialFunction[Throwable, MigrationState[T]] = {
    case t: Throwable => MigrationFailure(subject, mapping(t))
  }

  private def withResponse[T](response: T): PartialFunction[Throwable, T] = {
    case _ => response
  }

}
