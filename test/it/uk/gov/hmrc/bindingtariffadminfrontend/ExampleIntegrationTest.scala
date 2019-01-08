package uk.gov.hmrc.bindingtariffadminfrontend

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.integration.ServiceSpec

class ExampleIntegrationTest extends WordSpec with Matchers with ServiceSpec  {

  override def externalServices: Seq[String] = Seq.empty

  "This integration test" should {

  }

}
