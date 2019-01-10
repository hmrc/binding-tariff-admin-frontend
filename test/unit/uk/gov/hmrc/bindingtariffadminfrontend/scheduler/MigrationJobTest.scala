package uk.gov.hmrc.bindingtariffadminfrontend.scheduler

import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffadminfrontend.model.CaseMigration
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class MigrationJobTest extends UnitSpec with MockitoSugar {

  private val service = mock[DataMigrationService]
  private val job = new MigrationJob(service)

  "MigrationJob" should {
    val migration = mock[CaseMigration]

    "Configure 'name'" in {
      job.name shouldBe "DataMigration"
    }

    "Execute and call Service.Process" in {
      given(service.getUnprocessedMigrations) willReturn Future.successful(Seq(migration))
      given(service.process(any[CaseMigration])) willReturn Future.successful(migration)

      await(job.execute())

      verify(service).process(migration)
    }
  }

}
