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
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.connector._
import uk.gov.hmrc.bindingtariffadminfrontend.model.Store.Store
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.repository.UploadRepository
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ResetService @Inject() (
  uploadRepository: UploadRepository,
  fileConnector: FileStoreConnector,
  rulingConnector: RulingConnector,
  caseConnector: BindingTariffClassificationConnector,
  dataMigrationConnector: DataMigrationJsonConnector,
  dataMigrationService: DataMigrationService,
  appConfig: AppConfig
) {
  def resetEnvironment(stores: Set[Store])(implicit hc: HeaderCarrier): Future[Unit] =
    if (!appConfig.resetPermitted) {
      Future.failed(new UnauthorizedException("Not permitted to reset environment"))
    } else {
      reset(stores)
    }

  private def reset(stores: Set[Store])(implicit hc: HeaderCarrier): Future[Unit] = {
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
}
