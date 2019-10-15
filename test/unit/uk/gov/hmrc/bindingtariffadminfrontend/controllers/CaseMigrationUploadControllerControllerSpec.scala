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

package uk.gov.hmrc.bindingtariffadminfrontend.controllers

import java.io.{BufferedWriter, File, FileWriter}
import java.time.{Instant, LocalDate, ZoneOffset}

import akka.stream.Materializer
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.http.HeaderNames.LOCATION
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.i18n.{DefaultLangs, DefaultMessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsEmpty, MultipartFormData, Result}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.{Configuration, Environment}
import play.filters.csrf.CSRF.{Token, TokenProvider}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification._
import uk.gov.hmrc.bindingtariffadminfrontend.model.{MigratableCase, MigratableDecision, MigratableEvent, MigratedAttachment}
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future
import scala.io.Source

class CaseMigrationUploadControllerControllerSpec extends WordSpec with Matchers
  with UnitSpec with MockitoSugar with WithFakeApplication {

  private val fakeRequest = FakeRequest()
  private val env = Environment.simple()
  private val configuration = Configuration.load(env)
  private val migrationService = mock[DataMigrationService]
  private val messageApi = new DefaultMessagesApi(env, configuration, new DefaultLangs(configuration))
  private val appConfig = new AppConfig(configuration, env)
  private implicit val mat: Materializer = fakeApplication.materializer
  private val controller = new CaseMigrationUploadController(new SuccessfulAuthenticatedAction, migrationService, messageApi, appConfig)

  "GET /" should {
    "return 200" in {
      val result: Result = await(controller.get()(newFakeGETRequestWithCSRF))
      status(result) shouldBe OK
      bodyOf(result) should include("case_migration_upload-heading")
    }
  }

  "POST /" should {
    "Prepare Upload and Redirect To Migration State Controller" in {
      given(migrationService.prepareMigration(any[Seq[MigratableCase]], refEq(false))(any[HeaderCarrier])) willReturn Future.successful(true)

      val file = TemporaryFile(withJson(fromFile("migration.json")))
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")

      theMigrations shouldBe Seq(
        MigratableCase(
          reference = "reference",
          status = CaseStatus.CANCELLED,
          createdDate = "2018-01-01",
          daysElapsed = 1,
          referredDaysElapsed = Some(2),
          closedDate = Some("2018-01-01"),
          caseBoardsFileNumber = Some("Case Boards File Number"),
          assignee = Some(Operator("Assignee Id", Some("Assignee"))),
          queueId = Some("1"),
          application = BTIApplication(
            holder = EORIDetails("EORI", "Business Name", "Line 1", "Line 2", "Line 3", "Postcode", "GB"),
            contact = Contact("Contact Name", "contact.name@host.com", Some("Phone")),
            agent = None,
            goodName =  "Good Name",
            goodDescription = "Good Description",
            confidentialInformation = Some("Confidential Information"),
            otherInformation = Some("Other Information"),
            reissuedBTIReference = Some("Reissued Reference"),
            relatedBTIReference = Some("Related Reference"),
            knownLegalProceedings = Some("Known Legal Proceedings"),
            envisagedCommodityCode = Some("Envisaged Code")
          ),
          decision  = Some(MigratableDecision(
            bindingCommodityCode = "391990",
            effectiveStartDate = Some("2018-01-01"),
            effectiveEndDate = Some("2021-01-01"),
            justification = "Justification",
            goodsDescription = "Decision Good Description",
            methodSearch = Some("Method Search"),
            methodCommercialDenomination = Some("Commercial Denomination"),
            methodExclusion = Some("Method Exclusion"),
            appeal =  Some(Seq(Appeal("1", AppealStatus.IN_PROGRESS, AppealType.REVIEW))),
            cancellation = Some(Cancellation(CancelReason.ANNULLED))
          )),
          attachments = Seq(MigratedAttachment(public = false, "attachment.pdf", None, "2019-01-01")),
          events =  Seq(MigratableEvent(Note("Note"), Operator("Event Operator Id",  Some("Event Operator")), "2019-01-01")),
          keywords = Set("Keyword"),
          sampleStatus = Some(SampleStatus.AWAITING)
        )
      )
    }

    "Prepare Upload with priority flag" in {
      given(migrationService.prepareMigration(any[Seq[MigratableCase]], refEq(true))(any[HeaderCarrier])) willReturn Future.successful(true)

      val file = TemporaryFile(withJson(fromFile("migration.json")))
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form = MultipartFormData[TemporaryFile](dataParts = Map("priority" -> Seq("true")), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }

    "Prepare Upload with minimal data" in {
      given(migrationService.prepareMigration(any[Seq[MigratableCase]], refEq(true))(any[HeaderCarrier])) willReturn Future.successful(true)

      val file = TemporaryFile(withJson(fromFile("migration-minimal.json")))
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form = MultipartFormData[TemporaryFile](dataParts = Map("priority" -> Seq("true")), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }

    "Prepare Upload with minimal data and a single appeal field" in {
      given(migrationService.prepareMigration(any[Seq[MigratableCase]], refEq(true))(any[HeaderCarrier])) willReturn Future.successful(true)

      val file = TemporaryFile(withJson(fromFile("migration-minimal-single-appeal-field.json")))
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form = MultipartFormData[TemporaryFile](dataParts = Map("priority" -> Seq("true")), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }

    "Prepare Upload with minimal data and all appeal fields" in {
      given(migrationService.prepareMigration(any[Seq[MigratableCase]], refEq(true))(any[HeaderCarrier])) willReturn Future.successful(true)

      val file = TemporaryFile(withJson(fromFile("migration-minimal-all-appeal-fields.json")))
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form = MultipartFormData[TemporaryFile](dataParts = Map("priority" -> Seq("true")), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }

    "Prepare Upload with minimal data with multiple appeals" in {
      given(migrationService.prepareMigration(any[Seq[MigratableCase]], refEq(true))(any[HeaderCarrier])) willReturn Future.successful(true)

      val file = TemporaryFile(withJson(fromFile("migration-minimal-all-appeal-fields-multiple.json")))
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form = MultipartFormData[TemporaryFile](dataParts = Map("priority" -> Seq("true")), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }

    "return 200 with Json Errors" in {
      val file = TemporaryFile(withJson("[{}]"))
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe OK
      bodyOf(result) should include("Data Migration Failed")
    }

    "return 200 with Json Errors on invalid json" in {
      val file = TemporaryFile(withJson("xyz"))
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe OK
      bodyOf(result) should include("Data Migration Failed")
    }

    "Redirect to GET given no file" in {
      val form = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/case-migration")
    }

  }

  private def theMigrations: Seq[MigratableCase] = {
    val captor = ArgumentCaptor.forClass(classOf[Seq[MigratableCase]])
    verify(migrationService).prepareMigration(captor.capture(), any[Boolean])(any[HeaderCarrier])
    captor.getValue
  }

  private def newFakeGETRequestWithCSRF: FakeRequest[AnyContentAsEmpty.type] = {
    val tokenProvider: TokenProvider = fakeApplication.injector.instanceOf[TokenProvider]
    val csrfTags = Map(Token.NameRequestTag -> "csrfToken", Token.RequestTag -> tokenProvider.generateToken)
    FakeRequest("GET", "/", FakeHeaders(), AnyContentAsEmpty, tags = csrfTags)
  }

  private def withJson(json: String): File = {
    val file = File.createTempFile("tmp", ".json")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(json)
    bw.close()
    file
  }

  private def locationOf(result: Result): Option[String] = {
    result.header.headers.get(LOCATION)
  }

  private def fromFile(path: String): String = {
    val url = getClass.getClassLoader.getResource(path)
    Source.fromURL(url, "UTF-8").getLines().mkString
  }

  private implicit def str2instant: String => Instant = LocalDate.parse(_).atStartOfDay.toInstant(ZoneOffset.UTC)

}