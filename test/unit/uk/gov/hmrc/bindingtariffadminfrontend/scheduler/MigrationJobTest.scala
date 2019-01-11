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

import org.joda.time.Duration
import org.mockito.ArgumentMatchers.{any, anyString, refEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.{verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.{Case, CaseMigration}
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class MigrationJobTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val config = mock[AppConfig]
  private val service = mock[DataMigrationService]
  private val lockRepository = mock[LockRepository]
  private def job = new MigrationJob(config, service, lockRepository)

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.reset(service, lockRepository)
  }

  "MigrationJob" should {
    val `case` = mock[Case]
    given(`case`.reference) willReturn "reference"
    val migration = CaseMigration(`case`)

    "Configure 'lock Interval" in {
      given(config.dataMigrationLockLifetime) willReturn FiniteDuration(60, TimeUnit.SECONDS)
      job.releaseLockAfter shouldBe Duration.standardSeconds(60)
    }

    "Configure 'interval'" in {
      given(config.dataMigrationInterval) willReturn FiniteDuration(60, TimeUnit.SECONDS)
      job.interval shouldBe FiniteDuration(60, TimeUnit.SECONDS)
    }

    "Configure 'initial delay'" in {
      job.initialDelay shouldBe FiniteDuration(0, TimeUnit.SECONDS)
    }

    "Configure 'name'" in {
      job.name shouldBe "DataMigration"
    }

    "Execute and Delegate to the Service" in {
      given(lockRepository.lock(anyString, anyString, any())) willReturn Future.successful(true)
      given(lockRepository.releaseLock(anyString, anyString)) willReturn Future.successful(())
      given(service.getNextMigration) willReturn Future.successful(Some(migration))
      given(service.process(any[CaseMigration])(any[HeaderCarrier])) willReturn Future.successful(migration)

      await(job.execute).message shouldBe "Job with DataMigration run and completed with result [Migration with reference [reference] Succeeded]"

      verify(service).process(refEq(migration))(any[HeaderCarrier])
    }

    "Handle No Migrations Remaining" in {
      given(lockRepository.lock(anyString, anyString, any())) willReturn Future.successful(true)
      given(lockRepository.releaseLock(anyString, anyString)) willReturn Future.successful(())
      given(service.getNextMigration) willReturn Future.successful(None)

      await(job.execute).message shouldBe "Job with DataMigration run and completed with result [There was no migrations]"
    }

    "Not execute if lock exists" in {
      given(lockRepository.lock(anyString, anyString, any())) willReturn Future.successful(false)

      await(job.execute)

      verifyZeroInteractions(service)
    }
  }
}
