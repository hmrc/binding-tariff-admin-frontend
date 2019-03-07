package uk.gov.hmrc.bindingtariffadminfrontend

import java.io.InputStream

import org.apache.commons.io.IOUtils
import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.HttpVerbs
import play.api.inject.guice.GuiceApplicationBuilder
import scalaj.http.{Http, HttpResponse}
import uk.gov.hmrc.bindingtariffadminfrontend.connector.{ResourceFiles, WiremockFeatureTestServer}

trait IntegrationTest extends WiremockFeatureTestServer
  with Matchers
  with GivenWhenThen
  with GuiceOneServerPerSuite
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with ResourceFiles {

  protected val serviceUrl = s"http://localhost:$port/binding-tariff-admin"

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure("auth.credentials" -> "it:5E884898DA28047151D0E56F8DC6292773603D0D6AABBDD62A11EF721D1542D8")
    .configure(config)
    .build()

  protected def config: Map[String, String] = Map()

  protected def get[T](path: String = "", authenticated: Boolean = true)(implicit parser: String => T): HttpResponse[Option[T]] = {
    var request = Http(s"$serviceUrl/$path").method(HttpVerbs.GET)
    if(authenticated) {
      request = request.auth("it", "password")
    }
    request.execute((stream: InputStream) => Option(stream).map((s: InputStream) => IOUtils.toString(s)))
  }

}
