package uk.gov.hmrc.bindingtariffadminfrontend

class AdminIntegrationTest extends IntegrationTest {

  feature("Index Page") {
    scenario("User loads the index page") {
      val response = get()

      response.is2xx shouldBe true
    }

    scenario("User loads the index page without auth") {
      val response = get(authenticated = false)

      response.is4xx shouldBe true
    }
  }

}
