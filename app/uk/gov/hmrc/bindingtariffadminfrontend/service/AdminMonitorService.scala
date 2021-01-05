/*
 * Copyright 2021 HM Revenue & Customs
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

import uk.gov.hmrc.bindingtariffadminfrontend.connector.{BindingTariffClassificationConnector, FileStoreConnector}
import uk.gov.hmrc.bindingtariffadminfrontend.model.ScheduledJob.ScheduledJob
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.ApplicationType.ApplicationType
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification._
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileSearch, FileUploaded}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.UploadRepository
import uk.gov.hmrc.http.HeaderCarrier
import javax.inject.Inject

import uk.gov.hmrc.bindingtariffadminfrontend.model.MigrationJob.MigrationJob

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AdminMonitorService @Inject() (
  bindingTariffClassificationConnector: BindingTariffClassificationConnector,
  fileStoreConnector: FileStoreConnector,
  uploadRepository: UploadRepository
) {

  private val countPagination = Pagination(1, 1)

  def getStatistics(implicit hc: HeaderCarrier): Future[MonitorStatistics] =
    for {
      submittedCases      <- countSubmittedCases
      migratedCases       <- countMigratedCases
      publishedFiles      <- countPublishedFiles
      unpublishedFiles    <- countUnpublishedFiles
      uploadedAttachments <- countUploadedAttachments
    } yield MonitorStatistics(
      submittedCases          = submittedCases,
      migratedCases           = migratedCases,
      publishedFileCount      = publishedFiles,
      unpublishedFileCount    = unpublishedFiles,
      migratedAttachmentCount = uploadedAttachments
    )

  private def countSubmittedCases(implicit hc: HeaderCarrier): Future[Map[ApplicationType, Int]] =
    ApplicationType.values
      .foldLeft(Future.successful(Map.empty[ApplicationType, Int])) {
        case (m, applicationType) =>
          m.flatMap(map => caseCount(applicationType, migrated = false).map(count => map + (applicationType -> count)))
      }

  private def countMigratedCases(implicit hc: HeaderCarrier): Future[Map[ApplicationType, Int]] =
    ApplicationType.values
      .foldLeft(Future.successful(Map.empty[ApplicationType, Int])) {
        case (m, applicationType) =>
          m.flatMap(map => caseCount(applicationType, migrated = true).map(count => map + (applicationType -> count)))
      }

  private def caseCount(applicationType: ApplicationType, migrated: Boolean)(implicit hc: HeaderCarrier): Future[Int] =
    bindingTariffClassificationConnector
      .getCases(CaseSearch(migrated = Some(migrated), applicationType = Some(applicationType)), countPagination)
      .map(_.resultCount)

  private def countUploadedAttachments(implicit hc: HeaderCarrier): Future[Int] =
    uploadRepository
      .countType[AttachmentUpload]

  private def countPublishedFiles(implicit hc: HeaderCarrier): Future[Int] =
    fileStoreConnector
      .find(FileSearch(published = Some(true)), countPagination)
      .map(_.resultCount)

  private def countUnpublishedFiles(implicit hc: HeaderCarrier): Future[Int] =
    fileStoreConnector
      .find(FileSearch(published = Some(false)), countPagination)
      .map(_.resultCount)

  def getCases(search: CaseSearch, pagination: Pagination)(implicit hc: HeaderCarrier): Future[Paged[Case]] =
    bindingTariffClassificationConnector.getCases(search, pagination)

  def getEvents(search: EventSearch, pagination: Pagination)(implicit hc: HeaderCarrier): Future[Paged[Event]] =
    bindingTariffClassificationConnector.getEvents(search, pagination)

  def getFiles(search: FileSearch, pagination: Pagination)(implicit hc: HeaderCarrier): Future[Paged[FileUploaded]] =
    fileStoreConnector.find(search, pagination)

  def runScheduledJob(job: ScheduledJob)(implicit hc: HeaderCarrier): Future[Unit] =
    job match {
      case ScheduledJob.DAYS_ELAPSED =>
        bindingTariffClassificationConnector.runDaysElapsed
      case ScheduledJob.REFERRED_DAYS_ELAPSED =>
        bindingTariffClassificationConnector.runReferredDaysElapsed
      case _ => Future.failed(new IllegalArgumentException(s"Invalid Job [${job.toString}]"))
    }

  def runMigrationJob(job: MigrationJob)(implicit hc: HeaderCarrier): Future[Unit] =
    job match {
      case MigrationJob.AMEND_DATE_OF_EXTRACT =>
        bindingTariffClassificationConnector.runAmendDateOfExtractMigration
      case _ => Future.failed(new IllegalArgumentException(s"Invalid Job [${job.toString}]"))
    }

}
