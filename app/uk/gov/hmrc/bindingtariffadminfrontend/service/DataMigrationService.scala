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
import uk.gov.hmrc.bindingtariffadminfrontend.connector.{BindingTariffClassificationConnector, FileStoreConnector}
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigrationStatus.MigrationStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileUploaded, UploadRequest, UploadTemplate}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.MigrationRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class DataMigrationService @Inject()(repository: MigrationRepository,
                                     fileConnector: FileStoreConnector,
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

  def process(migration: Migration)(implicit hc: HeaderCarrier): Future[Migration] = {
    Logger.info(s"Case Migration with reference [${migration.`case`.reference}]: Starting")

    val uploads: Future[Seq[Try[MigratedAttachment]]] = for {
      // Delete any existing attachments that aren't in the migration
      _ <- deleteMissingAttachments(migration)

      // Create or Update The Case
      _ <- caseConnector.upsertCase(migration.`case`.toCase)

      //Publish The Files
      publishedUploads <- Future.sequence(migration.`case`.attachments.map(publish))

    } yield publishedUploads

    uploads map {
      // All Uploads were successful
      case u: Seq[Try[MigratedAttachment]] if u.count(_.isSuccess) == migration.`case`.attachments.size =>
        migration.copy(status = MigrationStatus.SUCCESS)

      // Not all Uploads were successful
      case u: Seq[Try[MigratedAttachment]] =>
        val successfulAttachments = u.filter(_.isSuccess).map(_.get.name)
        val failedAttachments = migration.`case`.attachments.filterNot(att => successfulAttachments.contains(att.name))
        val errorMessage = s"${failedAttachments.size}/${migration.`case`.attachments.size} Attachments Failed [${failedAttachments.map(_.name).mkString(", ")}]"
        migration.copy(status = MigrationStatus.PARTIAL_SUCCESS, message = Some(errorMessage))
    }
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
      _ <- clear() recover loggingAWarning
    } yield Unit
  }

  private def publish(attachment: MigratedAttachment)(implicit hc: HeaderCarrier): Future[Try[MigratedAttachment]] = {
    fileConnector.publish(attachment.name) map {
      _ => Success(attachment)
    } recover {
      case error => Failure(error)
    }
  }

  private def deleteMissingAttachments(migration: Migration)(implicit hc: HeaderCarrier): Future[Unit] = {
    caseConnector.getCase(migration.`case`.reference) flatMap {
      case Some(c) =>
        for {
          files <- if(c.attachments.nonEmpty) fileConnector.get(c.attachments.map(_.id)) else Future.successful(Seq.empty)
          newAttachmentNames = migration.`case`.attachments.map(_.name)
          missingFiles: Seq[FileUploaded] = files.filterNot(f => newAttachmentNames.contains(f.fileName))
          _ <- Future.sequence(missingFiles.map(f => fileConnector.delete(f.id)))
        } yield Unit

      case None =>
        Future.successful(Unit)
    }
  }

  def initiateFileMigation(file: UploadRequest)(implicit hc: HeaderCarrier): Future[UploadTemplate] = {
    fileConnector.initiate(file)
  }

}
