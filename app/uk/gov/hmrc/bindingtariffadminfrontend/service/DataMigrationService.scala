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

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import cats.syntax.all._
import javax.inject.Inject
import play.api.Logger
import play.api.libs.Files.TemporaryFile
import uk.gov.hmrc.bindingtariffadminfrontend.connector._
import uk.gov.hmrc.bindingtariffadminfrontend.lock.MigrationLock
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigrationStatus.MigrationStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model.Store.Store
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.Case
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileSearch, FileUploaded, UploadRequest, UploadTemplate}
import uk.gov.hmrc.bindingtariffadminfrontend.model.{MigrationStatus, _}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.{MigrationRepository, UploadRepository}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{sequence, successful}
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Random, Success}

class DataMigrationService @Inject() (
  repository: MigrationRepository,
  uploadRepository: UploadRepository,
  migrationLock: MigrationLock,
  fileConnector: FileStoreConnector,
  upscanS3Connector: UpscanS3Connector,
  rulingConnector: RulingConnector,
  caseConnector: BindingTariffClassificationConnector,
  dataMigrationConnector: DataMigrationJsonConnector,
  actorSystem: ActorSystem
) {

  // TODO: Delete when we upgrade to a version of Play that uses Akka Stream 2.6+
  implicit val materializer: Materializer = ActorMaterializer.create(actorSystem)

  private lazy val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

  def getDataMigrationFilesDetails(fileNames: List[String])(implicit hc: HeaderCarrier): Future[List[FileUploaded]] = {

    val opts = fileNames.map { file =>
      fileConnector.find(file).map {
        case Some(data) => data
        case None       => throw new RuntimeException("No data found")
      }
    }

    Future.sequence(opts)
  }

  def getUploadedBatch(batchId: String)(implicit hc: HeaderCarrier): Future[List[FileUploaded]] =
    for {
      uploads <- uploadRepository.getByBatch(batchId)
      results <- Future.sequence(uploads.map(_.id).map(fileConnector.find))
    } yield results.flatten

  def getState(status: Seq[MigrationStatus], pagination: Pagination): Future[Paged[Migration]] =
    repository.get(status, pagination)

  def counts: Future[MigrationCounts] =
    repository.countByStatus

  def prepareMigrationGroup(migrations: Seq[Migration], priority: Boolean)(
    implicit hc: HeaderCarrier
  ): Future[Boolean] =
    for {
      _      <- repository.delete(migrations)
      result <- repository.insert(migrations)
      _ <- if (priority) {
            Future.sequence(migrations.map(process(_).flatMap(update)))
          } else Future.successful(())
    } yield result

  def withMigrationLock(attemptNumber: Int, baseDelay: FiniteDuration, delayCap: FiniteDuration, maxRetries: Int)(
    block: => Future[Boolean]
  ): Future[Boolean] = {
    val exponentialBackoff = baseDelay.toMillis * math.pow(2, attemptNumber).longValue
    val backoffWithCap     = math.min(delayCap.toMillis, exponentialBackoff)
    val backoffWithJitter  = (backoffWithCap * Random.nextDouble()).longValue
    val retryDelay         = FiniteDuration(backoffWithJitter, TimeUnit.MILLISECONDS)

    if (attemptNumber > maxRetries) {
      Future.successful(false)
    } else {
      migrationLock.tryLock(block).flatMap {
        case Some(result) =>
          Future.successful(result)
        case None =>
          after(retryDelay, actorSystem.scheduler)(
            withMigrationLock(attemptNumber + 1, baseDelay, delayCap, maxRetries)(block)
          )
      }
    }
  }

  def prepareMigration(cases: Source[MigratableCase, _], priority: Boolean = false)(
    implicit hc: HeaderCarrier
  ): Future[Boolean] = {
    val groupSize = 5000

    withMigrationLock(attemptNumber = 0, baseDelay = 1.second, delayCap = 60.seconds, maxRetries = 10) {
      cases
        .map(Migration(_))
        .grouped(groupSize)
        .mapAsync(1)(prepareMigrationGroup(_, priority))
        .takeWhile(identity)
        .runWith(Sink.fold(true)(_ && _))
    }
  }

  def getNextMigration: Future[Option[Migration]] =
    repository.get(MigrationStatus.UNPROCESSED)

  def update(migration: Migration): Future[Option[Migration]] =
    repository.update(migration)

  def clear(status: Option[MigrationStatus] = None): Future[Boolean] =
    repository.delete(status)

  def resetEnvironment(stores: Set[Store])(implicit hc: HeaderCarrier): Future[Unit] = {

    def resetIfPresent(store: Store, expression: => Future[Any]): Future[Unit] =
      if (stores.contains(store)) {
        expression.map(_ => ()) recover loggingAWarning
      } else Future.successful(())

    def loggingAWarning: PartialFunction[Throwable, Unit] = {
      case t: Throwable => Logger.warn("Failed to clear Service", t)
    }

    for {
      _ <- resetIfPresent(Store.FILES, fileConnector.delete())
      _ <- resetIfPresent(Store.FILES, uploadRepository.deleteAll())
      _ <- resetIfPresent(Store.CASES, caseConnector.deleteCases())
      _ <- resetIfPresent(Store.EVENTS, caseConnector.deleteEvents())
      _ <- resetIfPresent(Store.RULINGS, rulingConnector.delete())
      _ <- resetIfPresent(Store.HISTORIC_DATA, dataMigrationConnector.deleteHistoricData())
      _ <- resetIfPresent(Store.MIGRATION, clear())
    } yield ()
  }

  def initiateFileMigration(upload: UploadRequest)(implicit hc: HeaderCarrier): Future[UploadTemplate] =
    fileConnector.initiate(upload)

  def upload(upload: UploadRequest, file: TemporaryFile)(implicit hc: HeaderCarrier): Future[Unit] =
    for {
      _        <- uploadRepository.update(upload)
      template <- fileConnector.initiate(upload)
      _        <- upscanS3Connector.upload(template, file, upload)
    } yield ()

  def process(migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] = {
    Logger.info(s"Case Migration with reference [${migration.`case`.reference}]: Starting")

    checkIfExists(migration).flatMap {
      case updatedMigration if updatedMigration.status != MigrationStatus.UNPROCESSED =>
        Future.successful(updatedMigration)
      case updatedMigration =>
        attemptMigration(updatedMigration)
    }
  }

  def attemptMigration(migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] =
    for {
      // Find the files for this Migration
      migratedFiles: Seq[FileUploaded] <- findMigratedFiles(migration)

      // Filter any un-migrated files from the migration & add a warning
      updated: Migration = filterUnMigratedAttachmentsFromTheMigration(migratedFiles, migration)

      // Create or Update The Case
      _ <- caseConnector.upsertCase(updated.`case`.toCase)

      // Create the events
      updated: Migration <- createEvents(updated)

      //Publish The Files
      updated: Migration <- publishUploads(migratedFiles, updated)

      // Notify The Ruling Store
      updated: Migration <- notifyRulingStore(updated)

      status = if (updated.status == MigrationStatus.UNPROCESSED) MigrationStatus.SUCCESS else updated.status

    } yield updated.copy(status = status)

  private def checkIfExists(migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] =
    caseConnector
      .getCase(migration.`case`.reference)
      .map {
        case Some(existingCase)
            if (migration.`case`.dateOfExtract, existingCase.dateOfExtract)
              .mapN((migrationDate, existingDate) => migrationDate isAfter existingDate)
              .getOrElse(false) =>
          val summaryMessage = "An earlier extract containing this case has already been uploaded"
          val detailMessages = detailCaseComparison(migration.`case`, existingCase)

          migration
            .copy(status = MigrationStatus.ABORTED)
            .appendMessage(summaryMessage)
            .appendMessage(detailMessages)

        case Some(existingCase)
            if (migration.`case`.dateOfExtract, existingCase.dateOfExtract)
              .mapN((migrationDate, existingDate) => migrationDate equals existingDate)
              .getOrElse(false) =>
          val summaryMessage = "The extract containing this case has already been uploaded"

          migration
            .copy(status = MigrationStatus.SKIPPED)
            .appendMessage(summaryMessage)

        case Some(existingCase) =>
          val summaryMessage = "A newer extract containing this case has already been uploaded"
          val detailMessages = detailCaseComparison(migration.`case`, existingCase)

          migration
            .copy(status = MigrationStatus.SKIPPED)
            .appendMessage(summaryMessage)
            .appendMessage(detailMessages)

        case _ =>
          migration
      }

  private def detailCaseComparison(migrationCase: MigratableCase, existingCase: Case): Seq[String] = {
    val info = existingCase.dateOfExtract.map { existingDate =>
      s"Previously migrated from the ${dateFormatter.format(existingDate)} extracts"
    }

    val warning = (migrationCase.dateOfExtract, existingCase.dateOfExtract).mapN { (migrationDate, existingDate) =>
      if (migrationDate isAfter existingDate) {
        Some(s"Newer information from this ${dateFormatter.format(migrationDate)} extract may be lost")
      } else {
        None
      }
    }.flatten

    val statusChange = if (existingCase.status != migrationCase.status) {
      Some(
        s"Status from migration [${migrationCase.status}] is different to the existing case [${existingCase.status}]"
      )
    } else {
      None
    }

    Seq(info, warning, statusChange).flatten
  }

  private def notifyRulingStore(migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] =
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

  private def createEvents(migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] =
    sequence(
      migration.`case`.events map { event =>
        caseConnector
          .createEvent(migration.`case`.reference, event.toEvent(migration.`case`.reference))
          .map(_ => MigrationSuccess(event)) recover withFailure(event)
      }
    ) map {
      case migrations: Seq[MigrationState[MigratableEvent]] if migrations.exists(_.isFailure) =>
        val failedMigrations = migrations.filter(_.isFailure).map(_.asFailure)
        val summaryMessage   = s"Failed to migrate ${failedMigrations.size}/${migrations.size} events"
        val failureMessages = failedMigrations.map(f =>
          s"Failed to migrate event [${f.subject.details.`type`}] because [${f.cause.getMessage}]"
        )

        migration
          .copy(status = MigrationStatus.PARTIAL_SUCCESS)
          .appendMessage(summaryMessage)
          .appendMessage(failureMessages)
      case _ =>
        migration
    }

  private def publishUploads(migratedFiles: Seq[FileUploaded], migration: Migration)(
    implicit hc: HeaderCarrier
  ): Future[Migration] = {
    val filesById: Map[String, FileUploaded] = migratedFiles.map(f => f.id -> f).toMap

    sequence(
      migration.`case`.attachments.map { a =>
        filesById.get(a.id) match {
          case Some(file) if file.published => Future.successful(MigrationSuccess(a))
          case Some(_)                      => fileConnector.publish(a.id).map(_ => MigrationSuccess(a)) recover withFailure(a)
          case None                         => Future.successful(MigrationFailure(a, MigrationFailedException("Not found")))
        }
      }
    ) map {
      case migrations: Seq[MigrationState[MigratedAttachment]] if migrations.exists(_.isFailure) =>
        val failedMigrations = migrations.filter(_.isFailure).map(_.asFailure)
        val summaryMessage   = s"Failed to migrate ${failedMigrations.size}/${migrations.size} attachments"
        val failureMessages =
          failedMigrations.map(f => s"Failed to migrate file [${f.subject.name}] because [${f.cause.getMessage}]")

        migration
          .copy(status = MigrationStatus.PARTIAL_SUCCESS)
          .appendMessage(summaryMessage)
          .appendMessage(failureMessages)
      case _ =>
        migration
    }
  }

  def findMigratedFiles(migration: Migration)(implicit hc: HeaderCarrier): Future[Seq[FileUploaded]] = {
    val newFiles   = migration.`case`.attachments
    val newFileIds = newFiles.map(_.id).toSet
    if (newFileIds.nonEmpty) {
      fileConnector.find(FileSearch(ids = Some(newFileIds)), Pagination.max).map(_.results) recover withResponse(
        Seq.empty
      )
    } else successful(Seq.empty)
  }

  private def filterUnMigratedAttachmentsFromTheMigration(
    filesMigrated: Seq[FileUploaded],
    migration: Migration
  ): Migration = {
    val newFiles                                        = migration.`case`.attachments
    val newFileIds                                      = newFiles.map(_.id).toSet
    val newFileIdsFound: Seq[String]                    = filesMigrated.map(_.id)
    val missingMigratableFiles: Seq[MigratedAttachment] = newFiles.filterNot(att => newFileIdsFound.contains(att.id))
    missingMigratableFiles match {
      case missing if missing.nonEmpty =>
        val summaryMessage  = s"Failed to migrate ${missing.size}/${newFileIds.size} attachments"
        val failureMessages = missing.map(f => s"Failed to migrate file [${f.name}] because [Not Found]")
        val `case`          = migration.`case`.copy(attachments = newFiles.filter(f => newFileIdsFound.contains(f.id)))
        migration
          .copy(status = MigrationStatus.PARTIAL_SUCCESS, `case` = `case`)
          .appendMessage(summaryMessage)
          .appendMessage(failureMessages)
      case _ =>
        migration
    }
  }

  private def withFailure[T](
    subject: T,
    mapping: Throwable => Throwable = t => t
  ): PartialFunction[Throwable, MigrationState[T]] = {
    case t: Throwable => MigrationFailure(subject, mapping(t))
  }

  private def withResponse[T](response: T): PartialFunction[Throwable, T] = {
    case _ => response
  }

}
