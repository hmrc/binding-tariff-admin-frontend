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

package uk.gov.hmrc.bindingtariffadminfrontend.scheduler

import java.util.concurrent.TimeUnit

import javax.inject.Inject
import org.joda.time.Duration
import play.api.Logger
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.{Migration, MigrationStatus}
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.scheduling.LockedScheduledJob

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class MigrationJob @Inject()(appConfig: AppConfig,
                             service: DataMigrationService,
                             override val lockRepository: LockRepository) extends LockedScheduledJob {

  private implicit val headers: HeaderCarrier = HeaderCarrier()

  override def name: String = "DataMigration"

  override val releaseLockAfter: Duration = Duration.millis(appConfig.dataMigrationLockLifetime.toMillis)

  override def initialDelay: FiniteDuration = FiniteDuration(0, TimeUnit.SECONDS)

  override def interval: FiniteDuration = appConfig.dataMigrationInterval

  override def executeInLock(implicit ec: ExecutionContext): Future[Result] = {
    Logger.debug(s"Running Job [$name]")
    process().map(count => Result(s"Processed $count migrations"))
  }

  private def process(count: Int = 0)(implicit ctx: ExecutionContext): Future[Int] = {
    service.getNextMigration flatMap {
      case Some(migration) if count < 100 =>
        process(migration).flatMap { result =>
          Logger.info(result.message)
          process(count + 1)
        }
      case _ => Future.successful(count)
    }
  }

  private def process(migration: Migration)(implicit ctx: ExecutionContext): Future[Result] = {
    service.process(migration) recover {
      case t: Throwable =>
        Logger.error(s"Migration with reference [${migration.`case`.reference}] failed", t)
        migration.copy(status = MigrationStatus.FAILED, message = Some(t.getMessage))
    } flatMap {
      service.update
    } map {
      case Some(processed) => Result(s"[Migration with reference [${processed.`case`.reference}] completed with status [${processed.status}]]")
      case None => Result(s"[Migration with reference [${migration.`case`.reference}] was cleared before it completed]")
    }
  }
}
