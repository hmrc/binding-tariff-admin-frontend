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

import java.io.{BufferedWriter, FileWriter}
import java.time.LocalDate

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.csv.scaladsl.CsvParsing.lineScanner
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status._
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.libs.json.Json
import play.api.libs.ws._
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsJson, MultipartFormData, Result}
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.bindingtariffadminfrontend.connector.DataTransformationConnector
import uk.gov.hmrc.bindingtariffadminfrontend.model.Anonymize
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileUploadSubmission, FileUploaded}
import uk.gov.hmrc.http._

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.duration._

class DataMigrationJsonControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val migrationConnector = mock[DataTransformationConnector]
  private val actorSystem        = mock[ActorSystem]

  override implicit val mat: Materializer              = fakeApplication.materializer
  override implicit val defaultTimeout: FiniteDuration = 30.seconds

  private val controller = new DataMigrationJsonController(
    authenticatedAction = new SuccessfulAuthenticatedAction,
    connector           = migrationConnector,
    system              = actorSystem,
    materializer        = mat,
    mcc                 = mcc,
    messagesApi         = messageApi,
    appConfig           = realConfig
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(migrationConnector)
  }

  private val extractionDate = LocalDate.of(2020, 10, 10)

  def fakeUploadFileRequest(uploadedFiles: List[FileUploaded]): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(
      "GET",
      "/",
      FakeHeaders(),
      AnyContentAsJson(Json.toJson(FileUploadSubmission(extractionDate, uploadedFiles)))
    ).withCSRFToken.asInstanceOf[FakeRequest[AnyContentAsEmpty.type]]

  private val csvList = List(
    "tblCaseClassMeth_csv",
    "historicCases_csv",
    "eBTI_Application_csv",
    "eBTI_Addresses_csv",
    "tblCaseRecord_csv",
    "tblCaseBTI_csv",
    "tblImages_csv",
    "tblCaseLMComments_csv",
    "tblMovement_csv",
    "Legal_Proceedings_csv",
    "TblCaseMiscCorres_csv"
  )

  private val anonymizedCsvList = csvList.filterNot(_ == "historicCases_csv")

  val aSuccessfullyUploadedFile: FileUploaded = FileUploaded("name", "published", "text/plain", None, None)

  private def mockCsvField(row: Int, col: Int): String = s"$row-$col"
  private val mockCsvRowCount                          = 250
  private def mockCsv(headers: List[String], rowCount: Int = mockCsvRowCount): String = {
    var result = headers.mkString(",")
    result += "\n"

    for (row <- 0 until rowCount) {
      var rowFields = ListBuffer[String]()
      for (col <- headers.indices) {
        rowFields += mockCsvField(row, col)
      }

      result += rowFields.mkString(",")
      result += "\n"
    }

    result
  }

  "getAnonymiseData /" should {

    "return 200" in {
      val result: Result = await(controller.getAnonymiseData()(newFakeRequestWithCSRF))

      status(result) shouldBe OK
    }
  }

  "anonymiseData /" should {

    "return 200" in {

      val filename: String = "file.txt"
      val mimeType: String = "application/csv"
      val data: MultipartFormData[TemporaryFile] = {
        val file     = SingletonTemporaryFileCreator.create(filename)
        val filePart = FilePart[TemporaryFile](key = "file", filename, contentType = Some(mimeType), ref = file)
        MultipartFormData[TemporaryFile](
          dataParts = Map("id" -> Seq(filename), "filename" -> Seq(filename), "mimetype" -> Seq(mimeType)),
          files     = Seq(filePart),
          badParts  = Seq.empty
        )
      }

      val result = await(controller.anonymiseData()(newFakeRequestWithCSRF.withBody(data)))

      status(result) shouldBe OK

    }

    "return valid anonymised files" in {
      anonymizedCsvList.foreach { filename =>
        val headers = (filename match {
          case _ if filename.contains("eBTI_Application") =>
            "CaseNo,PreviousBTIRef,PreviousBTICountry,PreviousDateofValidity,PreviousNomenclature,PreviousNomenclatureCode,CustomsNomenclature,OtherNomenclature,EnvisagedImport,EnvisagedNomenclature,GoodsDescription,AdditionalInfo,Attachment,Samples,ReturnSamples,OtherApplications,Country1,Place1,DateOfApplication1,OtherBTIRef1,DateOfValidity1,OtherNomenclatureCode1,Country2,Place2,DateOfApplication2,OtherBTIRef2,DateOfValidity2,OtherNomenclatureCode2,OtherHolders,HolderCountry1,HolderOtherBTIRef1,HolderDateOfValidity1,HolderOtherNomenclatureCode1,HolderCountry2,HolderOtherBTIRef2,HolderDateOfValidity2,HolderOtherNomenclatureCode2,Contact,VATRegTurnNo,Reference,Created,Updated,Status,CancelledDate,CancelledUser,FastTrack,TransactionType,CustomsOther,LegalProceedingsPending,AdditionalInfo2,EnvisagedSpecialDesc,Signature,SignatureDate,CombinedNomenclature,TARICCode,TARICAdditionalCode1,TARICAdditionalCode2,NationalAdditionalCode,BrochureIncluded,PhotoIncluded,OtherIncluded"
          case _ if filename.contains("eBTI_Addresses") =>
            "CaseNo,Name,Address1,Address2,Address3,Postcode,Country,TelephoneNo,FaxNo,CustomsID,Type,Reference,Created,Updated,City,Email"
          case _ if filename.contains("tblCaseRecord") =>
            "CaseNo,InsCreatorID,InsCreationDate,InsCountryCode,InsGoodsID,InsBoardFileDatePrinted,InsBoardFilePrinted,InsBoardFileUserName,CaseName,CaseType,CaseFastTrack,CaseCategory,CaseBoardsFileNumber,CaseStatus,CaseDownLoaded,CaseSampleIndicator,CaseAppealIndicator,CaseReceiptDate,CaseClosedDate,CaseClosedReason,CaseInterimReplySentDate,CaseCompletedDate,CaseBoardFileRequestDate,CaseAddress1,CaseAddress2,CaseAddress3,CaseAddress4,CaseAddress5,CasePostCode,CaseTelephoneNo,CaseFaxNo,CaseAgentName,CaseCrossRefIndicator,CaseTeamCompleted,CaseUserCompleted,CaseNameCompleted,CaseSearchText,CaseReplacedBy,CaseReplacing,LiabilityPort,LiabilityPortOfficerName,LiabilityPortOfficerLoc,LiabilityPortOfficerTel,LiabilityEntryNo,LiabilityEntryDate,LiabilityStatus,AppealReceivedDate,AppealStatus,AppealTribunalDate,AppealCompletionDate,AppealResult,AppealResultDate,SupressDate,SupressTeam,SupressUserName,SupressReason,ElapsedDays,ElapsedDaysInterim,ApplicationRef,AppealDownloaded,CancelDownloaded,BTILetterPrintDate,CustAuthKey,CaseCustomsID,CaseEmail,ContactName"
          case _ if filename.contains("tblCaseBTI") =>
            "CaseNo,StatusDate,Status,MISStatus,PrintedDate,StartValidityDate,ApplicationDate,Keywords1,Keywords2,Keywords3,Keywords4,Keywords5,Keywords6,Keywords7,Keywords8,Keywords9,Keywords10,Keywords11,Keywords12,Keywords13,Keywords14,Keywords15,Keywords16,Keywords17,Keywords18,Keywords19,Keywords20,EndValidityDate"
          case _ if filename.contains("tblCaseClassMeth") =>
            "CaseNo,BERTISearch,EBTISearch,Justification,CommercialDenomenation,GoodsDescription,Exclusions,LGCExpertAdvice,OTCCommodityCode,ApplicantsCommodityCode,OTCImage,DescriptionIncluded,BrochureIncluded,PhotoIncluded,SampleIncluded,OtherIncluded,CombinedNomenclature,TARICCode,TARICAdditionalCode1,TARICAdditionalCode2,NationalAdditionalCode"
          case _ if filename.contains("tblImages") =>
            "CaseNo,DateAdded,TimeAdded,Description,FileName,Counter,SendWithBTI,Confidential,SavedToFile,DeleteFlag,DeletedDate,DeletedTime,DeletingUserID,AddingUserID,IsApplicationAttachment,SendWithApp"
          case _ if filename.contains("tblMovement") =>
            "CaseNo,DateSent,TimeSent,SenderID,SenderTeam,RecipientTeam,RecipientType,DateReceived,TimeReceived,RecipientID,Reason"
          case _ if filename.contains("tblCaseLMComments") =>
            "CaseNo,Band7DateChecked,Band7TimeChecked,Band7Name,Band7User,Band7Satisfied,Band7Comments,Band9DateChecked,Band9TimeChecked,Band9Name,Band9User,Band9Satisfied,Band9Comments,Band11DateChecked,Band11TimeChecked,Band11Name,Band11User,Band11Satisfied,Band11Comments"
          case _ if filename.contains("Legal_Proceedings") =>
            "CaseNo,CourtName,StreetAndNumber,City,Postcode,Country,CourtCaseRefNo"
          case _ if filename.contains("TblCaseMiscCorres") =>
            "CaseNo,Comments"
          case _ => throw new Exception("Incomplete test")
        }).split(",").toList
        val mimeType: String = "application/csv"
        val data: MultipartFormData[TemporaryFile] = {
          val file   = SingletonTemporaryFileCreator.create(filename)
          val writer = new BufferedWriter(new FileWriter(file.file))
          writer.write(mockCsv(headers))
          writer.close()
          val filePart = FilePart[TemporaryFile](key = "file", filename, contentType = Some(mimeType), ref = file)
          MultipartFormData[TemporaryFile](
            dataParts = Map("id" -> Seq(filename), "filename" -> Seq(filename), "mimetype" -> Seq(mimeType)),
            files     = Seq(filePart),
            badParts  = Seq.empty
          )
        }

        // Read the output file
        val result     = await(controller.anonymiseData()(newFakeRequestWithCSRF.withBody(data)))
        val outputBody = await(result.body.consumeData)
        val outputLines = await(
          Source
            .single(outputBody)
            .via(lineScanner())
            .map(_.map(_.utf8String))
            .runWith(Sink.seq)
        )

        val outputHeaders  = outputLines.head
        val outputDataRows = outputLines.tail

        // Determine if a given field is anonymized (would be better if this could be obtained from a map)
        val isAnonymized =
          headers.map(header => (header, Anonymize.anonymize(filename, Map(header -> "TEST"))(header) != "TEST")).toMap

        // Ensure at least 1 field is anonymized
        isAnonymized.values.count(_ == true) >= 1 shouldBe true

        outputHeaders shouldBe headers

        for (row <- 0 until mockCsvRowCount) {
          for (col <- headers.indices) {
            if (isAnonymized(headers(col))) {
              outputDataRows(row)(col) shouldNot equal(mockCsvField(row, col))
            } else {
              outputDataRows(row)(col) shouldBe mockCsvField(row, col)
            }
          }
        }
      }
    }

    "return 400" in {
      val data: MultipartFormData[TemporaryFile] = {
        MultipartFormData[TemporaryFile](
          dataParts = Map("id" -> Seq.empty, "filename" -> Seq.empty, "mimetype" -> Seq.empty),
          files     = Seq.empty,
          badParts  = Seq.empty
        )
      }

      val result = await(controller.anonymiseData()(newFakeRequestWithCSRF.withBody(data)))

      status(result) shouldBe BAD_REQUEST

    }
  }

  "checkStatus /" should {

    "return 200" in {

      val result: Result = await(controller.checkStatus()(newFakeRequestWithCSRF))

      status(result) shouldBe OK
    }
  }

  "getStatusOfJsonProcessing /" should {

    "return 200" in {
      given(migrationConnector.getStatusOfJsonProcessing(any[HeaderCarrier]))
        .willReturn(Future.successful(HttpResponse.apply(200, responseJson = Some(Json.obj("status" -> "inserting")))))

      val result: Result = await(controller.getStatusOfJsonProcessing()(newFakeRequestWithCSRF))

      status(result)     shouldBe OK
      jsonBodyOf(result) shouldBe Json.obj("status" -> "inserting")
    }

    "return 400" in {
      given(migrationConnector.getStatusOfJsonProcessing(any[HeaderCarrier]))
        .willReturn(
          Future.successful(HttpResponse.apply(400, responseJson = Some(Json.obj("error" -> "error while inserting"))))
        )

      val result: Result = await(controller.getStatusOfJsonProcessing()(newFakeRequestWithCSRF))

      status(result)     shouldBe BAD_REQUEST
      jsonBodyOf(result) shouldBe Json.obj("error" -> "error while inserting")
    }
  }

  "downloadBTIJson /" should {

    "return 200" in {
      val json = Json.parse("""{
                              |  "href": "url",
                              |  "fields": {
                              |    "field": "value"
                              |  }
                              |}""".stripMargin)

      val jsonData = Source.single(ByteString.fromString(json.toString()))

      val response: WSResponse = mock[WSResponse]
      when(response.status).thenReturn(200)
      when(response.bodyAsSource: Source[ByteString, Any]).thenReturn(jsonData)

      given(migrationConnector.downloadBTIJson).willReturn(Future.successful(response))

      val result = await(controller.downloadBTIJson()(newFakeRequestWithCSRF))

      status(result)     shouldBe OK
      jsonBodyOf(result) shouldBe Json.parse("""{
                                               |  "href": "url",
                                               |  "fields": {
                                               |    "field": "value"
                                               |  }
                                               |}""".stripMargin)

    }

    "return 400" in {
      val json                 = Json.obj("error" -> "error while building josn")
      val response: WSResponse = mock[WSResponse]
      when(response.status).thenReturn(400)
      when(response.body).thenReturn(json.toString())

      given(migrationConnector.downloadBTIJson).willReturn(Future.successful(response))

      intercept[BadRequestException](
        await(controller.downloadBTIJson()(newFakeRequestWithCSRF))
      )
    }
  }

  "downloadLiabilitiesJson /" should {

    "return 200" in {
      val json = Json.parse("""{
                              |  "href": "url",
                              |  "fields": {
                              |    "field": "value"
                              |  }
                              |}""".stripMargin)

      val jsonData = Source.single(ByteString.fromString(json.toString()))

      val response: WSResponse = mock[WSResponse]
      when(response.status).thenReturn(200)
      when(response.bodyAsSource: Source[ByteString, Any]).thenReturn(jsonData)
      given(migrationConnector.downloadLiabilitiesJson).willReturn(Future.successful(response))

      val result = await(controller.downloadLiabilitiesJson()(newFakeRequestWithCSRF))

      status(result)     shouldBe OK
      jsonBodyOf(result) shouldBe Json.parse("""{
                                               |  "href": "url",
                                               |  "fields": {
                                               |    "field": "value"
                                               |  }
                                               |}""".stripMargin)

    }

    "return 400" in {
      val json                 = Json.obj("error" -> "error while building josn")
      val response: WSResponse = mock[WSResponse]
      when(response.status).thenReturn(400)
      when(response.body).thenReturn(json.toString())

      given(migrationConnector.downloadLiabilitiesJson).willReturn(Future.successful(response))

      intercept[BadRequestException](
        await(controller.downloadLiabilitiesJson()(newFakeRequestWithCSRF))
      )
    }
  }

  "downloadCorrespondenceJson /" should {

    "return 200" in {
      val json = Json.parse("""{
                              |  "href": "url",
                              |  "fields": {
                              |    "field": "value"
                              |  }
                              |}""".stripMargin)

      val jsonData = Source.single(ByteString.fromString(json.toString()))

      val response: WSResponse = mock[WSResponse]
      when(response.status).thenReturn(200)
      when(response.bodyAsSource: Source[ByteString, Any]).thenReturn(jsonData)
      given(migrationConnector.downloadCorrespondenceJson).willReturn(Future.successful(response))

      val result = await(controller.downloadCorrespondenceJson()(newFakeRequestWithCSRF))

      status(result)     shouldBe OK
      jsonBodyOf(result) shouldBe Json.parse("""{
                                               |  "href": "url",
                                               |  "fields": {
                                               |    "field": "value"
                                               |  }
                                               |}""".stripMargin)

    }

    "return 400" in {
      val json                 = Json.obj("error" -> "error while building josn")
      val response: WSResponse = mock[WSResponse]
      when(response.status).thenReturn(400)
      when(response.body).thenReturn(json.toString())

      given(migrationConnector.downloadCorrespondenceJson).willReturn(Future.successful(response))

      intercept[BadRequestException](
        await(controller.downloadCorrespondenceJson()(newFakeRequestWithCSRF))
      )
    }
  }

  "downloadMiscellaneousJson /" should {

    "return 200" in {
      val json = Json.parse("""{
                              |  "href": "url",
                              |  "fields": {
                              |    "field": "value"
                              |  }
                              |}""".stripMargin)

      val jsonData = Source.single(ByteString.fromString(json.toString()))

      val response: WSResponse = mock[WSResponse]
      when(response.status).thenReturn(200)
      when(response.bodyAsSource: Source[ByteString, Any]).thenReturn(jsonData)
      given(migrationConnector.downloadMiscellaneousJson).willReturn(Future.successful(response))

      val result = await(controller.downloadMiscellaneousJson()(newFakeRequestWithCSRF))

      status(result)     shouldBe OK
      jsonBodyOf(result) shouldBe Json.parse("""{
                                               |  "href": "url",
                                               |  "fields": {
                                               |    "field": "value"
                                               |  }
                                               |}""".stripMargin)

    }

    "return 400" in {
      val json                 = Json.obj("error" -> "error while building josn")
      val response: WSResponse = mock[WSResponse]
      when(response.status).thenReturn(400)
      when(response.body).thenReturn(json.toString())

      given(migrationConnector.downloadMiscellaneousJson).willReturn(Future.successful(response))

      intercept[BadRequestException](
        await(controller.downloadMiscellaneousJson()(newFakeRequestWithCSRF))
      )
    }
  }

  "downloadMigrationReports /" should {
    "return 200" in {
      val data = Source.single(ByteString.fromString("~~archive~~"))

      val response: WSResponse = mock[WSResponse]
      when(response.status).thenReturn(200)
      when(response.bodyAsSource: Source[ByteString, Any]).thenReturn(data)
      given(migrationConnector.downloadMigrationReports).willReturn(Future.successful(response))

      val result = await(controller.downloadMigrationReports()(newFakeRequestWithCSRF))

      status(result) shouldBe OK
      bodyOf(result) shouldBe "~~archive~~"
    }

    "return 400" in {
      val json                 = Json.obj("error" -> "error while building json")
      val response: WSResponse = mock[WSResponse]
      when(response.status).thenReturn(400)
      when(response.body).thenReturn(json.toString())

      given(migrationConnector.downloadMigrationReports).willReturn(Future.successful(response))

      intercept[BadRequestException](
        await(controller.downloadMigrationReports()(newFakeRequestWithCSRF))
      )
    }
  }
}
