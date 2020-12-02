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

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
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
  dataMigrationService: DataMigrationService,
  actorSystem: ActorSystem
) {
  implicit val materializer: Materializer = ActorMaterializer.create(actorSystem)

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
      count <- deleteCases(search = CaseSearch(migrated = Some(true)), pageSize = 512)
    } yield count

  private def deleteCases(search: CaseSearch, pageSize: Int)(
    implicit hc: HeaderCarrier
  ): Future[Int] =
    caseConnector
      .getCases(search, pagination = Pagination(1, pageSize))
      .flatMap { info =>
        Source(info.pageCount to 1 by -1)
          .mapAsync(1)(page =>
            caseConnector
              .getCases(search, Pagination(page, pageSize))
              .map(_.results)
          )
          .flatMapMerge(1, cases => Source(cases.toList))
          .mapAsync(1)(c => deleteCase(c))
          .runWith(Sink.ignore)
          .map(_ => info.resultCount)
      }

  private def deleteCase(`case`: Case)(implicit hc: HeaderCarrier): Future[Unit] = {
    def warning(message: String): PartialFunction[Throwable, Unit] = {
      case t: Throwable => Logger.warn(message, t)
    }

    for {
      _ <- rulingConnector.delete(`case`.reference) recover warning(s"Failed to delete ruling [${`case`.reference}]")
      _ <- Source(`case`.attachments.toList)
            .mapAsync(1) { attachment =>
              fileConnector.delete(attachment.id) recover warning(s"Failed to delete attachment [${attachment.id}]")
              uploadRepository.deleteById(attachment.id) recover warning(s"Failed to delete upload [${attachment.id}]")
            }
            .runWith(Sink.ignore)
      _ <- caseConnector.deleteCaseEvents(`case`.reference) recover warning(
            s"Failed to delete case [${`case`.reference}] events"
          )
      _ <- caseConnector.deleteCase(`case`.reference) recover warning(s"Failed to delete case [${`case`.reference}]")
    } yield ()
  }
}
