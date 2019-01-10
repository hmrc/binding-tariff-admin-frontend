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
import uk.gov.hmrc.bindingtariffadminfrontend.model.{Case, CaseMigration, MigrationCounts, MigrationStatus}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.CaseMigrationRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class DataMigrationServiceTest extends UnitSpec with MockitoSugar {

  private val repository = mock[CaseMigrationRepository]
  private val service = new DataMigrationService(repository)

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

  "Service 'Get Unprocessed'" should {
    val migration = mock[CaseMigration]
    val migrations = Seq(migration)

    "Delegate to Repository" in {
      given(repository.get(Some(MigrationStatus.UNPROCESSED))) willReturn Future.successful(migrations)
      await(service.getUnprocessedMigrations) shouldBe migrations
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

  "Service 'Process'"


  private def theMigrationsCreated: Seq[CaseMigration] = {
    val captor: ArgumentCaptor[Seq[CaseMigration]] = ArgumentCaptor.forClass(classOf[Seq[CaseMigration]])
    verify(repository).insert(captor.capture())
    captor.getValue
  }
}
