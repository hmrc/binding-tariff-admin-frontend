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
import uk.gov.hmrc.bindingtariffadminfrontend.connector.{BindingTariffClassificationConnector, FileStoreConnector, UpscanS3Connector}
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigrationStatus.MigrationStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileUploaded, UploadRequest, UploadTemplate}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.MigrationRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{sequence, successful}
import scala.util.{Failure, Success, Try}

class DataMigrationService @Inject()(repository: MigrationRepository,
                                     fileConnector: FileStoreConnector,
                                     upscanS3Connector: UpscanS3Connector,
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
      publishedUploads <- sequence(migration.`case`.attachments.map(publish))

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
    } yield ()
  }

  private def publish(attachment: MigratedAttachment)(implicit hc: HeaderCarrier): Future[Try[MigratedAttachment]] = {
    fileConnector.publish(attachment.id).map(_ => Success(attachment)).recover {
      case e => Failure(e)
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

  def initiateFileMigration(upload: UploadRequest)(implicit hc: HeaderCarrier): Future[UploadTemplate] = {
    fileConnector.initiate(upload)
  }

  def upload(upload: UploadRequest, file: TemporaryFile)(implicit hc: HeaderCarrier): Future[Unit] = {
    for {
      template <- fileConnector.initiate(upload)
      _ <- upscanS3Connector.upload(template, file)
    } yield ()
  }

}
