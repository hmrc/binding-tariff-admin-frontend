package uk.gov.hmrc.bindingtariffadminfrontend.service

import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffadminfrontend.repository.CaseMigrationRepository
import uk.gov.hmrc.play.test.UnitSpec

class DataMigrationServiceTest extends UnitSpec with MockitoSugar {


  private val repository = mock[CaseMigrationRepository]
  private val service = new DataMigrationService(repository)

  "Service 'Process'" should {
    "migrate a single case" in {

    }
  }
}
