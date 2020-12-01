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
import uk.gov.hmrc.bindingtariffadminfrontend.connector._
import uk.gov.hmrc.bindingtariffadminfrontend.model.Store.Store
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.{Case, CaseSearch}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.UploadRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ResetService @Inject() (
  uploadRepository: UploadRepository,
  fileConnector: FileStoreConnector,
  rulingConnector: RulingConnector,
  caseConnector: BindingTariffClassificationConnector,
  dataMigrationConnector: DataMigrationJsonConnector,
  dataMigrationService: DataMigrationService
) {
  def resetEnvironment(stores: Set[Store])(implicit hc: HeaderCarrier): Future[Unit] = {
    def resetIfPresent(store: Store, expression: => Future[Any]): Future[Unit] = {
      def loggingAWarning: PartialFunction[Throwable, Unit] = {
        case t: Throwable => Logger.warn("Failed to clear Service", t)
      }

      if (stores.contains(store)) {
        expression.map(_ => ()) recover loggingAWarning
      } else Future.successful(())
    }

    for {
      _ <- resetIfPresent(Store.FILES, fileConnector.delete())
      _ <- resetIfPresent(Store.FILES, uploadRepository.deleteAll())
      _ <- resetIfPresent(Store.CASES, caseConnector.deleteCases())
      _ <- resetIfPresent(Store.EVENTS, caseConnector.deleteEvents())
      _ <- resetIfPresent(Store.RULINGS, rulingConnector.delete())
      _ <- resetIfPresent(Store.HISTORIC_DATA, dataMigrationConnector.deleteHistoricData())
      _ <- resetIfPresent(Store.MIGRATION, dataMigrationService.clear(None))
    } yield ()
  }

  def resetMigratedCases()(implicit hc: HeaderCarrier): Future[Int] =
    for {
      _     <- resetEnvironment(Set(Store.MIGRATION))
      count <- deleteCases(search = CaseSearch(migrated = Some(true)), pageSize = 1024)
    } yield count

  private def deleteCases(search: CaseSearch, pageSize: Int)(implicit hc: HeaderCarrier): Future[Int] = {
    def deletePage(search: CaseSearch, pagination: Pagination): Future[Int] =
      caseConnector.getCases(search, pagination).flatMap { result =>
        val cases = result.results
        Future.sequence(cases.map(deleteCase)).map(_ => cases.size)
      }

    def deleteCase(`case`: Case): Future[Unit] =
      for {
        _ <- rulingConnector.delete(`case`.reference)
        _ <- Future.sequence(`case`.attachments.map(attachment => fileConnector.delete(attachment.id)))
        _ <- Future.sequence(`case`.attachments.map(attachment => uploadRepository.deleteById(attachment.id)))
        _ <- caseConnector.deleteCaseEvents(`case`.reference)
        _ <- caseConnector.deleteCase(`case`.reference)
      } yield ()

    caseConnector
      .getCases(search, pagination = Pagination(1, pageSize))
      .map(_.pageCount)
      .flatMap { pageCount =>
        Future
          .sequence {
            (1 to pageCount)
              .map(page => deletePage(search, Pagination(page, pageSize)))
          }
          .map(_.fold(0) { case (a, b) => a + b })
      }
  }
}
