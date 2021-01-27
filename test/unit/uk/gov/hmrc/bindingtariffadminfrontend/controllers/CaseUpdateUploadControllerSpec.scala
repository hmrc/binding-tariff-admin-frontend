/*
 * Copyright 2021 HM Revenue & Customs
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
import java.nio.file.{Files, Path, StandardCopyOption}
import java.time.{Instant, LocalDate, ZoneOffset}

import akka.stream.scaladsl.{Sink, Source}
import com.fasterxml.jackson.core.JsonParseException
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.http.HeaderNames.LOCATION
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.libs.json.{JsResultException, Json, Reads}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{MultipartFormData, Result}
import play.api.test.FakeRequest
import play.api.{Configuration, Environment}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.CaseUpdateTarget.CaseUpdateTarget
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification._
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class CaseUpdateUploadControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val fakeRequest      = FakeRequest()
  private val env              = Environment.simple()
  private val configuration    = Configuration.load(env)
  private val migrationService = mock[DataMigrationService]
  private val appConfig        = new AppConfig(configuration)

  private val controller = new CaseUpdateUploadController(
    authenticatedAction = new SuccessfulAuthenticatedAction,
    service             = migrationService,
    mcc                 = mcc,
    messagesApi         = messageApi,
    appConfig           = appConfig
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(migrationService)
  }

  "GET /" should {
    "return 200" in {
      val result: Result = await(controller.get()(FakeRequest("GET", "/")))
      status(result) shouldBe OK
      bodyOf(result) should include("case_update_upload-heading")
    }
  }

  "POST /" should {
    val expectedMigrations = Seq(
      MigratableCase(
        reference            = "reference",
        status               = CaseStatus.CANCELLED,
        createdDate          = "2018-01-01",
        daysElapsed          = 1,
        referredDaysElapsed  = Some(2),
        closedDate           = Some("2018-01-01"),
        caseBoardsFileNumber = Some("Case Boards File Number"),
        assignee             = Some(Operator("Assignee Id", Some("Assignee"))),
        queueId              = Some("1"),
        application = BTIApplication(
          holder                  = EORIDetails("EORI", "Business Name", "Line 1", "Line 2", "Line 3", "Postcode", "GB"),
          contact                 = Contact("Contact Name", "contact.name@host.com", Some("Phone")),
          agent                   = None,
          goodName                = "Good Name",
          goodDescription         = "Good Description",
          confidentialInformation = Some("Confidential Information"),
          otherInformation        = Some("Other Information"),
          reissuedBTIReference    = Some("Reissued Reference"),
          relatedBTIReference     = Some("Related Reference"),
          relatedBTIReferences    = List("Related References"),
          knownLegalProceedings   = Some("Known Legal Proceedings"),
          envisagedCommodityCode  = Some("Envisaged Code")
        ),
        decision = Some(
          MigratableDecision(
            bindingCommodityCode         = "391990",
            effectiveStartDate           = Some("2018-01-01"),
            effectiveEndDate             = Some("2021-01-01"),
            justification                = "Justification",
            goodsDescription             = "Decision Good Description",
            methodSearch                 = Some("Method Search"),
            methodCommercialDenomination = Some("Commercial Denomination"),
            methodExclusion              = Some("Method Exclusion"),
            appeal                       = Some(Seq(Appeal("1", AppealStatus.IN_PROGRESS, AppealType.REVIEW))),
            cancellation                 = Some(Cancellation(CancelReason.ANNULLED))
          )
        ),
        attachments = Seq(MigratedAttachment(public = false, "attachment.pdf", None, "2019-01-01")),
        events =
          Seq(MigratableEvent(Note("Note"), Operator("Event Operator Id", Some("Event Operator")), "2019-01-01")),
        keywords     = Set("Keyword"),
        sampleStatus = Some(SampleStatus.AWAITING)
      ),
      MigratableCase(
        reference            = "111111111",
        status               = CaseStatus.COMPLETED,
        createdDate          = Instant.parse("2011-01-01T12:00:00Z"),
        daysElapsed          = 27,
        closedDate           = Some(Instant.parse("2012-05-14T12:00:00Z")),
        caseBoardsFileNumber = Some("111"),
        assignee             = Some(Operator(id = "7099633", name = Some("7099633"))),
        queueId              = None,
        application = LiabilityOrder(
          contact = Contact(
            name  = "PortOfficer1",
            email = "-",
            phone = Some("PortLoc1")
          ),
          goodName            = None,
          status              = LiabilityStatus.NON_LIVE,
          traderName          = "ContactName1",
          entryDate           = Some(Instant.parse("2011-01-01T12:00:00Z")),
          entryNumber         = Some("1"),
          traderCommodityCode = Some("1000000000"),
          dateOfReceipt       = Some(Instant.parse("2001-01-01T12:00:00Z")),
          traderContactDetails = Some(
            TraderContactDetails(
              email = Some("email1@example.com"),
              phone = Some("phone1"),
              address = Some(
                Address(
                  buildingAndStreet = "address1-1",
                  townOrCity        = "address1-1",
                  county            = Some("address3-1\naddress4-1\naddress5-1"),
                  postCode          = Some("PCODE1")
                )
              )
            )
          )
        ),
        decision = Some(
          MigratableDecision(
            bindingCommodityCode         = "0100000000",
            effectiveStartDate           = None,
            effectiveEndDate             = None,
            justification                = "Justification 1",
            goodsDescription             = "Description 1",
            methodSearch                 = Some("BertiSearch1\nEBTISearch1"),
            methodCommercialDenomination = None,
            methodExclusion              = Some("Exclusions 1"),
            appeal                       = None,
            cancellation                 = None
          )
        ),
        attachments = Nil,
        events = Seq(
          MigratableEvent(
            CompletedCaseStatusChange(
              from    = CaseStatus.OPEN,
              comment = Some("Case completed"),
              email   = None
            ),
            operator = Operator(
              id   = "7099633",
              name = Some("7099633")
            ),
            timestamp = Instant.parse("2012-05-14T12:00:00Z")
          ),
          MigratableEvent(
            Note("Liability case created"),
            operator = Operator(
              "1234567",
              Some("1234567")
            ),
            timestamp = Instant.parse("2011-01-01T12:00:00Z")
          ),
          MigratableEvent(
            Note("Band7Comments 1"),
            operator = Operator(
              "1111",
              Some("1111")
            ),
            timestamp = Instant.parse("2009-05-11T15:30:00Z")
          ),
          MigratableEvent(
            Note("C592 received"),
            operator = Operator(
              "1234567",
              Some("1234567")
            ),
            timestamp = Instant.parse("2001-01-01T12:00:00Z")
          )
        ),
        keywords     = Set(),
        sampleStatus = None
      )
    )

    "Prepare Upload and Redirect To Migration State Controller with a plain JSON file" in {
      given(
        migrationService.prepareCaseUpdates(
          any[Source[MigratableCase, _]],
          refEq(false),
          refEq(CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME)
        )(any[HeaderCarrier])
      ) willReturn Future
        .successful(true)

      val file                                                       = SingletonTemporaryFileCreator.create(withJson(fromFile("migration.json")))
      val filePart                                                   = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form                                                       = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME)(postRequest))
      status(result)     shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")

      Await.result(theMigrations.runWith(Sink.seq), 10.seconds) shouldBe expectedMigrations
    }

    "Prepare Upload and Redirect To Migration State Controller with a zipped JSON file" in {
      given(
        migrationService.prepareCaseUpdates(
          any[Source[MigratableCase, _]],
          refEq(false),
          refEq(CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME)
        )(any[HeaderCarrier])
      ) willReturn Future
        .successful(true)

      val file = SingletonTemporaryFileCreator.create(withZip("migration.zip"))
      val filePart =
        FilePart[TemporaryFile](key = "file", "file.zip", contentType = Some("application/zip"), ref = file)
      val form                                                       = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME)(postRequest))
      status(result)     shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")

      Await.result(theMigrations.runWith(Sink.seq), 10.seconds) shouldBe expectedMigrations
    }

    "Prepare Upload with priority flag" in {
      given(
        migrationService.prepareCaseUpdates(
          any[Source[MigratableCase, _]],
          refEq(true),
          refEq(CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME)
        )(any[HeaderCarrier])
      ) willReturn Future
        .successful(true)

      val file     = SingletonTemporaryFileCreator.create(withJson(fromFile("migration.json")))
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form = MultipartFormData[TemporaryFile](
        dataParts = Map("priority" -> Seq("true")),
        files     = Seq(filePart),
        badParts  = Seq.empty
      )
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME)(postRequest))
      status(result)     shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/state")
    }

    "return 200 with Json Errors" in {
      val jsonString = "[{}]"
      val jsonError = intercept[JsResultException] {
        Json.parse(jsonString).as[List[MigratableCase]](Reads.list[MigratableCase](MigratableCase.REST.format))
      }
      given(
        migrationService.prepareCaseUpdates(
          any[Source[MigratableCase, _]],
          refEq(false),
          refEq(CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME)
        )(any[HeaderCarrier])
      ) willReturn Future.failed(jsonError)

      val file                                                       = SingletonTemporaryFileCreator.create(withJson(jsonString))
      val filePart                                                   = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form                                                       = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME)(postRequest))
      status(result) shouldBe OK
      bodyOf(result) should include("Case Updates Failed")
    }

    "return 200 with Json Errors on invalid json" in {
      val jsonString = "xyz"
      val jsonError = intercept[JsonParseException] {
        Json.parse(jsonString).as[List[MigratableCase]](Reads.list[MigratableCase](MigratableCase.REST.format))
      }
      given(
        migrationService.prepareCaseUpdates(
          any[Source[MigratableCase, _]],
          refEq(false),
          refEq(CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME)
        )(any[HeaderCarrier])
      ) willReturn Future
        .failed(jsonError)

      val file                                                       = SingletonTemporaryFileCreator.create(withJson(jsonString))
      val filePart                                                   = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = file)
      val form                                                       = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME)(postRequest))
      status(result) shouldBe OK
      bodyOf(result) should include("Case Updates Failed")
    }

    "Redirect to GET given no file" in {
      val form                                                       = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(), badParts = Seq.empty)
      val postRequest: FakeRequest[MultipartFormData[TemporaryFile]] = fakeRequest.withBody(form)

      val result: Result = await(controller.post(CaseUpdateTarget.LIABILITIES_APPLICATION_TRADERNAME)(postRequest))
      status(result)     shouldBe SEE_OTHER
      locationOf(result) shouldBe Some("/binding-tariff-admin/case-migration")
    }

  }

  private def theMigrations: Source[MigratableCase, _] = {
    val captor = ArgumentCaptor.forClass(classOf[Source[MigratableCase, _]])
    verify(migrationService, atLeastOnce())
      .prepareCaseUpdates(captor.capture(), any[Boolean], any[CaseUpdateTarget])(any[HeaderCarrier])
    captor.getValue
  }

  private def withJson(json: String): Path = {
    val file = File.createTempFile("tmp", ".json")
    val bw   = new BufferedWriter(new FileWriter(file))
    bw.write(json)
    bw.close()
    file.toPath
  }

  private def withZip(path: String): Path = {
    val resourceStream = getClass.getClassLoader.getResourceAsStream(path)
    val tempFile       = File.createTempFile("tmp", ".zip")
    Files.copy(resourceStream, tempFile.toPath, StandardCopyOption.REPLACE_EXISTING)
    tempFile.getAbsoluteFile().toPath
  }

  private def locationOf(result: Result): Option[String] =
    result.header.headers.get(LOCATION)

  private def fromFile(path: String): String = {
    val url = getClass.getClassLoader.getResource(path)
    scala.io.Source.fromURL(url, "UTF-8").getLines().mkString
  }

  private implicit def str2instant: String => Instant = LocalDate.parse(_).atStartOfDay.toInstant(ZoneOffset.UTC)

}
