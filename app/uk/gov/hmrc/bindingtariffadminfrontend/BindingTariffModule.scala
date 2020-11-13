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

package uk.gov.hmrc.bindingtariffadminfrontend

import javax.inject.{Inject, Provider}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DefaultDB
import uk.gov.hmrc.bindingtariffadminfrontend.scheduler.{MigrationJob, Scheduler}
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.scheduling.ScheduledJob

class BindingTariffModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ScheduledJob].to[MigrationJob],
    bind[LockRepository].toProvider[LockRepositoryProvider],
    bind[Scheduler].toSelf.eagerly()
  )

}

class LockRepositoryProvider @Inject() (component: ReactiveMongoComponent) extends Provider[LockRepository] {
  private implicit val db: () => DefaultDB = component.mongoConnector.db

  override def get(): LockRepository = new LockRepository
}
