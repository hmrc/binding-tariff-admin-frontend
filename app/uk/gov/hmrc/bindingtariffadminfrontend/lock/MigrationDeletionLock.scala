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

package uk.gov.hmrc.bindingtariffadminfrontend.lock

import javax.inject.{Inject, Singleton}
import org.joda.time.Duration
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}

@Singleton
class MigrationDeletionLock @Inject() (lockRepository: LockRepository, appConfig: AppConfig) extends LockKeeper {
  val lockName                        = "MigrationDeletion"
  val repo                            = lockRepository
  val lockId                          = s"$lockName-lock"
  val forceLockReleaseAfter: Duration = Duration.millis(appConfig.dataMigrationLockLifetime.toMillis)
}
