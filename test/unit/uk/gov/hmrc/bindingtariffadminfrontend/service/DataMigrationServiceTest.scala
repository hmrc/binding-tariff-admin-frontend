package uk.gov.hmrc.bindingtariffadminfrontend.service

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffadminfrontend.model.{Case, CaseMigration, MigrationStatus}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.CaseMigrationRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class DataMigrationServiceTest extends UnitSpec with MockitoSugar {

  private val repository = mock[CaseMigrationRepository]
  private val service = new DataMigrationService(repository)

  "Service 'Is Processing'" should {
    "Call Repository" in {
      given(repository.containsUnprocessedEntities) willReturn Future.successful(true)
      await(service.isProcessing) shouldBe true

      given(repository.containsUnprocessedEntities) willReturn Future.successful(false)
      await(service.isProcessing) shouldBe false
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

  private def theMigrationsCreated: Seq[CaseMigration] = {
    val captor: ArgumentCaptor[Seq[CaseMigration]] = ArgumentCaptor.forClass(classOf[Seq[CaseMigration]])
    verify(repository).insert(captor.capture())
    captor.getValue
  }
}
