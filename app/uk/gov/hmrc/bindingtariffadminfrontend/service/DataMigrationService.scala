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
import uk.gov.hmrc.bindingtariffadminfrontend.connector.BindingTariffClassificationConnector
import uk.gov.hmrc.bindingtariffadminfrontend.model.{Case, CaseMigration, MigrationCounts, MigrationStatus}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.CaseMigrationRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DataMigrationService @Inject()(repository: CaseMigrationRepository, connector: BindingTariffClassificationConnector) {

  def getState: Future[Seq[CaseMigration]] = {
    repository.get()
  }

  def counts: Future[MigrationCounts] = {
    repository.countByStatus
  }

  def prepareMigration(migrations: Seq[Case]): Future[Boolean] = {
    repository.insert(migrations.map(CaseMigration(_)))
  }

  def getNextMigration: Future[Option[CaseMigration]] = {
    repository.get(MigrationStatus.UNPROCESSED)
  }

  def process(c: CaseMigration)(implicit hc: HeaderCarrier): Future[CaseMigration] = {
    Logger.info(s"Case Migration with reference [${c.`case`.reference}]: Starting")
    connector.upsertCase(c.`case`)
      .map(_ => c.copy(status = MigrationStatus.SUCCESS))
      .recover({
        case t: Throwable =>
          Logger.error(s"Case Migration with reference [${c.`case`.reference}]: Failed", t)
          c.copy(status = MigrationStatus.FAILED, message = Some(t.getMessage))
      })
      .flatMap {
        repository
          .update(_)
          .map(_.getOrElse(throw new RuntimeException("Update failed")))
      }
  }

}
