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

import javax.inject.Inject
import play.api.Logger
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success

class MigrationJob @Inject()(service: DataMigrationService) extends Job {
  override def name: String = "DataMigration"

  override def execute(): Future[Unit] = {
    service.getUnprocessedMigrations.map {
      _.foreach(migration => {
        service.process(migration)(HeaderCarrier())
          .onComplete {
            case Success(_) =>
              Logger.info(s"Migration with reference [${migration.`case`.reference}] Succeeded")
            case _ =>
              Logger.info(s"Migration with reference [${migration.`case`.reference}] Failed with message [${migration.message.getOrElse("n/a")}]")
          }
      })
    }
  }
}
