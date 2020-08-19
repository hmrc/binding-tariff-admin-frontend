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
import uk.gov.hmrc.bindingtariffadminfrontend.connector.{BindingTariffClassificationConnector, FileStoreConnector}
import uk.gov.hmrc.bindingtariffadminfrontend.model.ScheduledJob.ScheduledJob
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.{Case, CaseSearch, Event, EventSearch}
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileSearch, FileUploaded}
import uk.gov.hmrc.bindingtariffadminfrontend.model.{Paged, Pagination, ScheduledJob}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AdminMonitorService @Inject()(bindingTariffClassificationConnector: BindingTariffClassificationConnector, fileStoreConnector: FileStoreConnector) {

  private val countPagination = Pagination(1, 1)

  def countCases(implicit hc: HeaderCarrier): Future[Int] = {
    bindingTariffClassificationConnector.getCases(CaseSearch(), countPagination).map(_.resultCount)
  }

  def countUnpublishedFiles(implicit hc: HeaderCarrier): Future[Int] = {
    fileStoreConnector.find(FileSearch(published = Some(false)), countPagination).map(_.resultCount)
  }

  def countPublishedFiles(implicit hc: HeaderCarrier): Future[Int] = {
    fileStoreConnector.find(FileSearch(published = Some(true)), countPagination).map(_.resultCount)
  }

  def getCases(search: CaseSearch, pagination: Pagination)(implicit hc: HeaderCarrier): Future[Paged[Case]] = {
    bindingTariffClassificationConnector.getCases(search, pagination)
  }

  def getEvents(search: EventSearch, pagination: Pagination)(implicit hc: HeaderCarrier): Future[Paged[Event]] = {
    bindingTariffClassificationConnector.getEvents(search, pagination)
  }

  def getFiles(search: FileSearch, pagination: Pagination)(implicit hc: HeaderCarrier): Future[Paged[FileUploaded]] = {
    fileStoreConnector.find(search, pagination)
  }

  def runScheduledJob(job: ScheduledJob)(implicit hc: HeaderCarrier): Future[Unit] = {
    job match {
      case ScheduledJob.DAYS_ELAPSED =>
        bindingTariffClassificationConnector.runDaysElapsed
      case ScheduledJob.REFERRED_DAYS_ELAPSED =>
        bindingTariffClassificationConnector.runReferredDaysElapsed
      case _ => Future.failed(new IllegalArgumentException(s"Invalid Job [${job.toString}]"))
    }
  }

}
