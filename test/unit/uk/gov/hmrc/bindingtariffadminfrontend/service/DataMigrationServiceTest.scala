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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffadminfrontend.connector.BindingTariffClassificationConnector
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.repository.CaseMigrationRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class DataMigrationServiceTest extends UnitSpec with MockitoSugar {

  private val repository = mock[CaseMigrationRepository]
  private val connector = mock[BindingTariffClassificationConnector]
  private val service = new DataMigrationService(repository, connector)
  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  "Service 'Counts'" should {

    "Delegate to Repository" in {
      val counts = mock[MigrationCounts]
      given(repository.countByStatus) willReturn Future.successful(counts)
      await(service.counts) shouldBe counts
    }
  }

  "Service 'Get State'" should {
    val migration = mock[CaseMigration]
    val migrations = Seq(migration)

    "Delegate to Repository" in {
      given(repository.get()) willReturn Future.successful(migrations)
      await(service.getState) shouldBe migrations
    }
  }

  "Service 'Get Next Unprocessed'" should {
    val migration = mock[CaseMigration]

    "Delegate to Repository" in {
      given(repository.get(MigrationStatus.UNPROCESSED)) willReturn Future.successful(Some(migration))
      await(service.getNextMigration) shouldBe Some(migration)
    }
  }

  "Service 'Prepare Migration'" should {
    val `case` = mock[Case]

    "Delegate to Repository" in {
      given(repository.insert(any[Seq[CaseMigration]])) willReturn Future.successful(true)

      await(service.prepareMigration(Seq(`case`))) shouldBe true

      theMigrationsCreated shouldBe Seq(
        CaseMigration(`case`, MigrationStatus.UNPROCESSED, None)
      )
    }
  }

  "Service 'Process'" should {
    val aCase = Cases.btiCaseExample
    val anUnprocessedMigration = CaseMigration(aCase)

    "Send the case to the backend via the connector and update the status to SUCCESS in the repository" in {
      val aSuccessfullyProcessedMigration = CaseMigration(aCase, MigrationStatus.SUCCESS)
      given(connector.upsertCase(any[Case])(any[HeaderCarrier])) willReturn Future.successful(aCase)
      given(repository.update(any[CaseMigration])) willReturn Future.successful(Some(aSuccessfullyProcessedMigration))

      await(service.process(anUnprocessedMigration)).status shouldBe MigrationStatus.SUCCESS

      theCaseSentToTheBackend shouldBe aCase
      theMigrationToBeUpdated shouldBe aSuccessfullyProcessedMigration
    }

    "update the status to FAILED in the repository when sending to the backend fails" in {
      val aFailedProcessedMigration = CaseMigration(aCase, MigrationStatus.FAILED)
      given(connector.upsertCase(any[Case])(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Boom!"))
      given(repository.update(any[CaseMigration])) willReturn Future.successful(Some(aFailedProcessedMigration))

      await(service.process(anUnprocessedMigration)).status shouldBe MigrationStatus.FAILED
    }

    "throw exception on update failure" in {
      given(connector.upsertCase(any[Case])(any[HeaderCarrier])) willReturn Future.successful(aCase)
      given(repository.update(any[CaseMigration])) willReturn Future.successful(None)

      intercept[RuntimeException] {
        await(service.process(anUnprocessedMigration))
      }.getMessage shouldBe "Update failed"
    }

  }

  "Service clear" should {
    "Delegate to repository" in {
      given(service.clear()) willReturn Future.successful(true)
      await(service.clear()) shouldBe true
    }

    "Delegate to repository with status" in {
      given(service.clear(Some(MigrationStatus.SUCCESS))) willReturn Future.successful(true)
      await(service.clear(Some(MigrationStatus.SUCCESS))) shouldBe true
    }
  }

  private def theMigrationToBeUpdated: CaseMigration = {
    val captor: ArgumentCaptor[CaseMigration] = ArgumentCaptor.forClass(classOf[CaseMigration])
    verify(repository).update(captor.capture())
    captor.getValue
  }

  private def theCaseSentToTheBackend: Case = {
    val captor: ArgumentCaptor[Case] = ArgumentCaptor.forClass(classOf[Case])
    verify(connector).upsertCase(captor.capture())(any())
    captor.getValue
  }

  private def theMigrationsCreated: Seq[CaseMigration] = {
    val captor: ArgumentCaptor[Seq[CaseMigration]] = ArgumentCaptor.forClass(classOf[Seq[CaseMigration]])
    verify(repository).insert(captor.capture())
    captor.getValue
  }
}
