package uk.gov.hmrc.bindingtariffadminfrontend

class AdminIntegrationTest extends IntegrationTest {

  feature("Index Page") {
    scenario("User loads the index page") {
      val response = get()

      response.code shouldBe 200
    }

    scenario("User loads the index page without auth") {
      val response = get(authenticated = false)

      response.is4xx shouldBe true
    }
  }

  feature("State Page") {
    scenario("User loads the state page") {
      val response = get(path = "state")

      response.code shouldBe 200
    }

    scenario("User loads the state page without auth") {
      val response = get(path = "state", authenticated = false)

      response.is4xx shouldBe true
    }
  }

  feature("Reset Page") {

    scenario("User loads the reset page") {

      val response = get(path = "reset")

      response.code shouldBe 200
    }

    scenario("User loads the reset page without auth") {
      val response = get(path = "reset", authenticated = false)

      response.is4xx shouldBe true
    }
  }

}
