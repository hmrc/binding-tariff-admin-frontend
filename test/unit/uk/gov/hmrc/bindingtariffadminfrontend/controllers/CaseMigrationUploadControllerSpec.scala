/*
 * Copyright 2020 HM Revenue & Customs
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

import scala.io
import scala.concurrent.Future
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink
import uk.gov.hmrc.bindingtariffadminfrontend.repository.MigrationRepository
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.bindingtariffadminfrontend.lock.MigrationLock
import uk.gov.hmrc.bindingtariffadminfrontend.connector.FileStoreConnector
import uk.gov.hmrc.bindingtariffadminfrontend.connector.RulingConnector
import uk.gov.hmrc.bindingtariffadminfrontend.connector.UpscanS3Connector
import uk.gov.hmrc.bindingtariffadminfrontend.connector.BindingTariffClassificationConnector
import akka.actor.ActorSystem
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.bindingtariffadminfrontend.model.Migration
import java.nio.file.Files
import java.nio.file.CopyOption
import java.nio.file.StandardCopyOption

class CaseMigrationUploadControllerSpec extends WordSpec with Matchers
  with UnitSpec with MockitoSugar with WithFakeApplication with BeforeAndAfterEach {

  private val fakeRequest = FakeRequest()
  private val env = Environment.simple()
  private val configuration = Configuration.load(env)
  private val repository = mock[MigrationRepository]
  private val fileConnector = mock[FileStoreConnector]
  private val rulingConnector = mock[RulingConnector]
  private val upscanS3Connector = mock[UpscanS3Connector]
  private val caseConnector = mock[BindingTariffClassificationConnector]
  private val lockRepository = mock[LockRepository]
  private val appConfig = new AppConfig(configuration, env)
  private def migrationLock = new MigrationLock(lockRepository, appConfig)
  private def actorSystem = ActorSystem.create("testActorSystem")
  private val migrationService = new DataMigrationService(repository, migrationLock, fileConnector, upscanS3Connector, rulingConnector, caseConnector, actorSystem)
  private val messageApi = new DefaultMessagesApi(env, configuration, new DefaultLangs(configuration))
  private implicit val mat: Materializer = fakeApplication.materializer
  private val controller = new CaseMigrationUploadController(new SuccessfulAuthenticatedAction, migrationService, messageApi, appConfig)

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(repository, caseConnector, fileConnector, rulingConnector, upscanS3Connector, lockRepository)
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    when(lockRepository.lock(anyString(), anyString(), any())) thenReturn Future.successful(true)
    when(lockRepository.releaseLock(anyString(), anyString())) thenReturn Future.successful(())
  }

  "GET /" should {
    "return 200" in {
      val result: Result = await(controller.get()(FakeRequest("GET", "/")))
      status(result) shouldBe OK
      bodyOf(result) should include("case_migration_upload-heading")
    }
  }

  "POST /" should {
    val expectedMigrations = Seq(
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

    "Prepare Upload and Redirect To Migration State Controller with a plain JSON file" in {
      given(repository.delete(any[Seq[Migration]])) willReturn Future.successful(true)
      given(repository.insert(any[Seq[Migration]])) willReturn Future.successful(true)

      val file = TemporaryFile(withJson(fromFile("migration.json")))
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")

      theMigrations shouldBe expectedMigrations
    }

    "Prepare Upload and Redirect To Migration State Controller with a zipped JSON file" in {
      given(repository.delete(any[Seq[Migration]])) willReturn Future.successful(true)
      given(repository.insert(any[Seq[Migration]])) willReturn Future.successful(true)

      val file = TemporaryFile(withZip("migration.zip"))
      val filePart = FilePart[TemporaryFile](key = "file", "file.zip", contentType = Some("application/zip"), ref = file)
      val form = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")

      theMigrations shouldBe expectedMigrations
    }

    // TODO: Fix these tests! When we changed this suite we found that they do not test anything;
    // they mocked the main method that this controller calls in DataMigrationService so all of
    // the different inputs provided are totally irrelevant as none of the associated logic is
    // ever called.
    //
    // When we changed the test suite so that we no longer mock the DataMigrationService we discovered
    // that to make these tests work we would need to mock responses from a bunch of other services,
    // such as the FileStoreConnector, CaseConnector and RulingConnector.
    //

    /*"Prepare Upload with priority flag" in {
      given(repository.delete(any[Seq[Migration]])) willReturn Future.successful(true)
      given(repository.insert(any[Seq[Migration]])) willReturn Future.successful(true)

      val file = TemporaryFile(withJson(fromFile("migration.json")))
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form = MultipartFormData[TemporaryFile](dataParts = Map("priority" -> Seq("true")), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }

    "Prepare Upload with minimal data" in {
      given(repository.delete(any[Seq[Migration]])) willReturn Future.successful(true)
      given(repository.insert(any[Seq[Migration]])) willReturn Future.successful(true)

      val file = TemporaryFile(withJson(fromFile("migration-minimal.json")))
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form = MultipartFormData[TemporaryFile](dataParts = Map("priority" -> Seq("true")), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }

    "Prepare Upload with minimal data and a single appeal field" in {
      given(repository.delete(any[Seq[Migration]])) willReturn Future.successful(true)
      given(repository.insert(any[Seq[Migration]])) willReturn Future.successful(true)

      val file = TemporaryFile(withJson(fromFile("migration-minimal-single-appeal-field.json")))
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form = MultipartFormData[TemporaryFile](dataParts = Map("priority" -> Seq("true")), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }

    "Prepare Upload with minimal data and all appeal fields" in {
      given(repository.delete(any[Seq[Migration]])) willReturn Future.successful(true)
      given(repository.insert(any[Seq[Migration]])) willReturn Future.successful(true)

      val file = TemporaryFile(withJson(fromFile("migration-minimal-all-appeal-fields.json")))
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form = MultipartFormData[TemporaryFile](dataParts = Map("priority" -> Seq("true")), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }

    "Prepare Upload with minimal data with multiple appeals" in {
      given(repository.delete(any[Seq[Migration]])) willReturn Future.successful(true)
      given(repository.insert(any[Seq[Migration]])) willReturn Future.successful(true)

      val file = TemporaryFile(withJson(fromFile("migration-minimal-all-appeal-fields-multiple.json")))
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form = MultipartFormData[TemporaryFile](dataParts = Map("priority" -> Seq("true")), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(postRequest))
      status(result) shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }*/

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
    val captor = ArgumentCaptor.forClass(classOf[Seq[Migration]])
    verify(repository).insert(captor.capture())
    captor.getValue.map(_.`case`)
  }

  private def withJson(json: String): File = {
    val file = File.createTempFile("tmp", ".json")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(json)
    bw.close()
    file
  }

  private def withZip(path: String): File = {
    val resourceStream = getClass.getClassLoader.getResourceAsStream(path)
    val tempFile = File.createTempFile("tmp", ".zip")
    Files.copy(resourceStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    tempFile.getAbsoluteFile()
  }

  private def locationOf(result: Result): Option[String] = {
    result.header.headers.get(LOCATION)
  }

  private def fromFile(path: String): String = {
    val url = getClass.getClassLoader.getResource(path)
    io.Source.fromURL(url, "UTF-8").getLines().mkString
  }

  private implicit def str2instant: String => Instant = LocalDate.parse(_).atStartOfDay.toInstant(ZoneOffset.UTC)

}