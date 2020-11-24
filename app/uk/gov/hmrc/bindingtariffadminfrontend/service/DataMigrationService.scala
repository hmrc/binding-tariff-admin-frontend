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
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.{Attachment, Case, Sample}
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileSearch, FileUploaded}
import uk.gov.hmrc.bindingtariffadminfrontend.model.{MigrationStatus, _}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.{MigrationRepository, UploadRepository}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.sequence
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Random, Success}

class DataMigrationService @Inject() (
  migrationRepository: MigrationRepository,
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

  def getUploadedBatch(batchId: String)(implicit hc: HeaderCarrier): Future[List[FileUploaded]] =
    for {
      uploads <- uploadRepository.getByBatch(batchId)
      results <- Future.sequence(uploads.map(_.id).map(fileConnector.find))
    } yield results.flatten

  def getState(status: Seq[MigrationStatus], pagination: Pagination): Future[Paged[Migration]] =
    migrationRepository.get(status, pagination)

  def counts: Future[MigrationCounts] =
    migrationRepository.countByStatus

  def prepareMigrationGroup(migrations: Seq[Migration], priority: Boolean)(
    implicit hc: HeaderCarrier
  ): Future[Boolean] =
    for {
      _      <- migrationRepository.delete(migrations)
      result <- migrationRepository.insert(migrations)
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
    migrationRepository.get(MigrationStatus.UNPROCESSED)

  def update(migration: Migration): Future[Option[Migration]] =
    migrationRepository.update(migration)

  def clear(status: Option[MigrationStatus] = None): Future[Boolean] =
    migrationRepository.delete(status)

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

  def upload(upload: Upload, file: TemporaryFile)(implicit hc: HeaderCarrier): Future[Unit] =
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
      // Find uploaded attachments
      uploadedAttachments: Seq[FileUploaded] <- findUploadedAttachments(migration)

      // Publish the available uploaded attachments, and remove any failed attachments from the case
      updated: Migration <- publishUploadedAttachments(uploadedAttachments, migration)

      // Create or Update The Case
      _ <- caseConnector.upsertCase(convertCase(updated.`case`, uploadedAttachments))

      // Create the events
      updated: Migration <- createEvents(updated)

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

  private def publishUploadedAttachments(uploadedAttachments: Seq[FileUploaded], migration: Migration)(
    implicit hc: HeaderCarrier
  ): Future[Migration] = {
    val missingFailures =
      migration.`case`.attachments
        .filterNot(att => uploadedAttachments.exists(_.fileName == att.name))
        .map(att => MigrationFailure(att, MigrationFailedException("Not Found")))

    val attachmentsByName  = migration.`case`.attachments.map(att => (att.name, att)).toMap
    val unpublishedUploads = uploadedAttachments.filterNot(_.published)

    sequence(
      unpublishedUploads.map { file =>
        val attachment = attachmentsByName(file.fileName)
        fileConnector.publish(file.id).map(_ => MigrationSuccess(attachment)) recover withFailure(
          attachment
        )
      }
    ).map(_.filter(_.isFailure).map(_.asFailure))
      .map(publishFailures => missingFailures ++ publishFailures)
      .map {
        case failures if failures.nonEmpty =>
          val availableAttachments =
            migration.`case`.attachments.filterNot(att => failures.exists(_.subject.name == att.name))

          val summaryMessage = s"Failed to migrate ${failures.size}/${migration.`case`.attachments.size} attachments"
          val failureMessages =
            failures.map(f => s"Failed to migrate file [${f.subject.name}] because [${f.cause.getMessage}]")
          val `case` = migration.`case`.copy(attachments = availableAttachments)

          migration
            .copy(status = MigrationStatus.PARTIAL_SUCCESS, `case` = `case`)
            .appendMessage(summaryMessage)
            .appendMessage(failureMessages)
        case _ =>
          migration
      }
  }

  def findUploadedAttachments(migration: Migration)(implicit hc: HeaderCarrier): Future[Seq[FileUploaded]] = {
    val attachmentFileNames = migration.`case`.attachments.map(_.name).toList

    if (attachmentFileNames.nonEmpty) {
      val uploadedAttachments = for {
        uploadedRequests <- uploadRepository.getByFileNames(attachmentFileNames)
        fileSearch       <- fileConnector.find(FileSearch(ids = Some(uploadedRequests.map(_.id).toSet)), Pagination.max)
      } yield fileSearch.results

      uploadedAttachments recover withResponse(Seq.empty)
    } else {
      Future.successful(Seq.empty)
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

  private def convertCase(migratableCase: MigratableCase, uploadedAttachments: Seq[FileUploaded]): Case = {
    val sample = migratableCase.sampleStatus match {
      case Some(s) => Sample(status = Some(s))
      case _       => Sample()
    }

    val attachments = for {
      att <- migratableCase.attachments
      id = uploadedAttachments.find(_.fileName == att.name).map(_.id)
      if id.isDefined
    } yield Attachment(
      id          = id.get,
      public      = att.public,
      operator    = att.operator,
      timestamp   = att.timestamp,
      description = att.description
    )

    Case(
      reference            = migratableCase.reference,
      status               = migratableCase.status,
      createdDate          = migratableCase.createdDate,
      daysElapsed          = migratableCase.daysElapsed,
      referredDaysElapsed  = migratableCase.referredDaysElapsed.getOrElse(0),
      closedDate           = migratableCase.closedDate,
      caseBoardsFileNumber = migratableCase.caseBoardsFileNumber,
      assignee             = migratableCase.assignee,
      queueId              = migratableCase.queueId,
      application          = migratableCase.application,
      decision             = migratableCase.decision.map(_.toDecision),
      attachments          = attachments,
      keywords             = migratableCase.keywords,
      sample               = sample,
      dateOfExtract        = migratableCase.dateOfExtract,
      migratedDaysElapsed  = migratableCase.migratedDaysElapsed
    )
  }
}
