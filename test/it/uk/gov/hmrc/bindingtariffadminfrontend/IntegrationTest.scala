package uk.gov.hmrc.bindingtariffadminfrontend

import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDate

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

  private lazy val hash: String = sha256(LocalDate.now().getYear + ":password")
  protected val serviceUrl = s"http://localhost:$port/binding-tariff-admin"

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure("auth.credentials" -> s"it:$hash")
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

  private def sha256(value: String): String = {
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes("UTF-8"))
      .map("%02x".format(_)).mkString.toUpperCase()
  }

}
