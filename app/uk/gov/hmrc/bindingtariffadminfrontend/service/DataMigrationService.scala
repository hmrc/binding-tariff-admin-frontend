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
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.Attachment
import uk.gov.hmrc.bindingtariffadminfrontend.repository.MigrationRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class DataMigrationService @Inject()(repository: MigrationRepository,
                                     fileConnector: FileStoreConnector,
                                     caseConnector: BindingTariffClassificationConnector) {

  def getState: Future[Seq[Migration]] = {
    repository.get()
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
      // If the case exists we cannot tell if all its attachments are up to date with what is in the Migration.
      // The only option is to remove them all and re-upload
      _ <- removeAnyExistingAttachments(migration)

      // Try Upload The Files
      uploads: Seq[Try[MigratedAttachment]] <- Future.sequence(
        migration.`case`.attachments.map(upload)
      )

      // Attachments
      successfulAttachments: Seq[Attachment] = uploads.filter(_.isSuccess).map(_.get.asAttachment)
      successfulUploads: Seq[MigratedAttachment] = uploads.filter(_.isSuccess).map(_.get)

      // Upsert The Case
      _ <- caseConnector.upsertCase(migration.`case`.toCase(successfulAttachments))

      //Publish The Files
      publishedUploads <- Future.sequence(successfulUploads.map(publish))

    } yield publishedUploads

    uploads map {
      // All Uploads were successful
      case u: Seq[Try[MigratedAttachment]] if u.count(_.isSuccess) == migration.`case`.attachments.size =>
        migration.copy(status = MigrationStatus.SUCCESS)

      // Not all Uploads were successful
      case u: Seq[Try[MigratedAttachment]] =>
        val successfulAttachments = u.filter(_.isSuccess).map(_.get.id)
        val failedAttachments = migration.`case`.attachments.filterNot(att => successfulAttachments.contains(att.id))
        val errorMessage = s"${failedAttachments.size}/${migration.`case`.attachments.size} Attachments Failed [${failedAttachments.map(_.url).mkString(", ")}]"
        migration.copy(status = MigrationStatus.PARTIAL_SUCCESS, message = Some(errorMessage))
    }
  }

  private def upload(migration: MigratableAttachment): Future[Try[MigratedAttachment]] = {
    fileConnector.upload(migration) map { uploaded =>
      Success(
        MigratedAttachment(
          id = migration.id,
          filestoreId = uploaded.id,
          public = migration.public,
          name = migration.name,
          mimeType = migration.mimeType,
          user = migration.user,
          timestamp = migration.timestamp
        )
      )
    } recover {
      case error => Failure(error)
    }
  }

  def delete(attachment: Attachment)(implicit hc: HeaderCarrier): Future[Unit] = {
    fileConnector.delete(attachment.id)
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
    fileConnector.publish(attachment.filestoreId) map {
      _ => Success(attachment)
    } recover {
      case error => Failure(error)
    }
  }

  private def removeAnyExistingAttachments(migration: Migration)(implicit hc: HeaderCarrier): Future[Unit] = {
    caseConnector.getCase(migration.`case`.reference) flatMap {
      case Some(c) => Future.sequence(c.attachments.map(delete)).map(_ => Unit)
      case None => Future.successful(Unit)
    }
  }

}
