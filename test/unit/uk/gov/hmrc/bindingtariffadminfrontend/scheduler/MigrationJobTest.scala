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

package uk.gov.hmrc.bindingtariffadminfrontend.scheduler

import java.util.concurrent.TimeUnit

import org.joda.time.Duration
import org.mockito.ArgumentMatchers.{any, anyString, refEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, verifyZeroInteractions}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.{MigratableCase, Migration, MigrationStatus}
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lock.LockRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import uk.gov.hmrc.bindingtariffadminfrontend.lock.MigrationLock

class MigrationJobTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val config         = mock[AppConfig]
  private val service        = mock[DataMigrationService]
  private val lockRepository = mock[LockRepository]
  private val appConfig      = mock[AppConfig]
  private def migrationLock  = new MigrationLock(lockRepository, appConfig)

  private def withJob(test: MigrationJob => Any) =
    test(new MigrationJob(config, service, migrationLock, lockRepository))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.when(appConfig.dataMigrationLockLifetime) thenReturn FiniteDuration(60, TimeUnit.SECONDS)
    Mockito.when(lockRepository.lock(anyString(), anyString(), any())) thenReturn Future.successful(true)
    Mockito.when(lockRepository.releaseLock(anyString(), anyString())) thenReturn Future.successful(())
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.reset(service, appConfig, lockRepository)
  }

  "MigrationJob" should {
    given(config.dataMigrationLockLifetime) willReturn FiniteDuration(60, TimeUnit.SECONDS)
    given(config.dataMigrationInterval) willReturn FiniteDuration(60, TimeUnit.SECONDS)

    val `case` = mock[MigratableCase]
    given(`case`.reference) willReturn "reference"

    "Configure 'lock Interval" in withJob(job => job.releaseLockAfter shouldBe Duration.standardSeconds(60))

    "Configure 'interval'" in withJob(job => job.interval shouldBe FiniteDuration(60, TimeUnit.SECONDS))

    "Configure 'initial delay'" in withJob(job => job.initialDelay shouldBe FiniteDuration(0, TimeUnit.SECONDS))

    "Configure 'name'" in withJob(job => job.name shouldBe "DataMigration")
  }

  "Execute" should {
    given(config.dataMigrationLockLifetime) willReturn FiniteDuration(60, TimeUnit.SECONDS)
    given(config.dataMigrationInterval) willReturn FiniteDuration(60, TimeUnit.SECONDS)

    val `case` = mock[MigratableCase]
    given(`case`.reference) willReturn "reference"
    val migration = Migration(`case`)

    "Execute in Lock Successfully" in withJob { job =>
      val migrationComplete = Migration(`case`, MigrationStatus.SUCCESS)
      given(lockRepository.lock(anyString, anyString, any())) willReturn Future.successful(true)
      given(lockRepository.releaseLock(anyString, anyString)) willReturn Future.successful(())
      given(service.getNextMigration) willReturn Future.successful(Some(migration)) willReturn Future.successful(None)
      given(service.process(any[Migration])(any[HeaderCarrier])) willReturn Future.successful(migrationComplete)
      given(service.update(any[Migration])) will returnWhatWasUpdated

      await(job.execute).message shouldBe "Job with DataMigration run and completed with result Processed 1 migrations"

      verify(service).process(refEq(migration))(any[HeaderCarrier])
      verify(service).update(migrationComplete)
    }

    "Execute batches of 100" in withJob { job =>
      val migrationComplete = Migration(`case`, MigrationStatus.SUCCESS)
      given(lockRepository.lock(anyString, anyString, any())) willReturn Future.successful(true)
      given(lockRepository.releaseLock(anyString, anyString)) willReturn Future.successful(())
      given(service.getNextMigration) willReturn Future.successful(Some(migration))
      given(service.process(any[Migration])(any[HeaderCarrier])) willReturn Future.successful(migrationComplete)
      given(service.update(any[Migration])) will returnWhatWasUpdated

      await(job.execute).message shouldBe "Job with DataMigration run and completed with result Processed 100 migrations"

      verify(service, times(100)).process(any[Migration])(any[HeaderCarrier])
      verify(service, times(100)).update(migrationComplete)
    }

    "Execute in Lock handling Cleared" in withJob { job =>
      val migrationComplete = Migration(`case`, MigrationStatus.SUCCESS)
      given(lockRepository.lock(anyString, anyString, any())) willReturn Future.successful(true)
      given(lockRepository.releaseLock(anyString, anyString)) willReturn Future.successful(())
      given(service.getNextMigration) willReturn Future.successful(Some(migration)) willReturn Future.successful(None)
      given(service.process(any[Migration])(any[HeaderCarrier])) willReturn Future.successful(migrationComplete)
      given(service.update(any[Migration])) willReturn Future.successful(None)

      await(job.execute).message shouldBe "Job with DataMigration run and completed with result Processed 1 migrations"

      verify(service).process(refEq(migration))(any[HeaderCarrier])
      verify(service).update(migrationComplete)
    }

    "Execute in Lock handling Failure" in withJob { job =>
      given(lockRepository.lock(anyString, anyString, any())) willReturn Future.successful(true)
      given(lockRepository.releaseLock(anyString, anyString)) willReturn Future.successful(())
      given(service.getNextMigration) willReturn Future.successful(Some(migration)) willReturn Future.successful(None)
      given(service.process(any[Migration])(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(service.update(any[Migration])) will returnWhatWasUpdated

      await(job.execute).message shouldBe "Job with DataMigration run and completed with result Processed 1 migrations"

      verify(service).process(refEq(migration))(any[HeaderCarrier])
      verify(service).update(Migration(`case`, status = MigrationStatus.FAILED, message = Seq("Error")))
    }

    "Handle No Migrations Remaining" in withJob { job =>
      given(lockRepository.lock(anyString, anyString, any())) willReturn Future.successful(true)
      given(lockRepository.releaseLock(anyString, anyString)) willReturn Future.successful(())
      given(service.getNextMigration) willReturn Future.successful(None)

      await(job.execute).message shouldBe "Job with DataMigration run and completed with result Processed 0 migrations"
    }

    "Not execute if lock exists" in withJob { job =>
      given(lockRepository.lock(anyString, anyString, any())) willReturn Future.successful(false)

      await(job.execute)

      verifyZeroInteractions(service)
    }

    def returnWhatWasUpdated: Answer[Future[Option[Migration]]] = new Answer[Future[Option[Migration]]] {
      override def answer(invocation: InvocationOnMock): Future[Option[Migration]] =
        Future.successful(Some(invocation.getArgument(0)))
    }
  }
}
