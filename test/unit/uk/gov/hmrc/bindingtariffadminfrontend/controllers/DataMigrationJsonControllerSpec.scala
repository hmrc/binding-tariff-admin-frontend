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

import java.io.{BufferedWriter, FileWriter}

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import play.api.http.Status._
import play.api.i18n.{DefaultLangs, DefaultMessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.libs.ws.{DefaultWSResponseHeaders, StreamedResponse}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsEmpty, MultipartFormData, Result}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.{Configuration, Environment}
import play.filters.csrf.CSRF.{Token, TokenProvider}
import uk.gov.hmrc.bindingtariffadminfrontend.akka_fix.csv.CsvParsing.lineScanner
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.connector.DataMigrationJsonConnector
import uk.gov.hmrc.bindingtariffadminfrontend.model.Anonymize
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.FileUploaded
import uk.gov.hmrc.bindingtariffadminfrontend.service.DataMigrationService
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

class DataMigrationJsonControllerSpec extends WordSpec with Matchers
  with UnitSpec with MockitoSugar with WithFakeApplication with BeforeAndAfterEach {

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)
  private val migrationService = mock[DataMigrationService]
  private val migrationConnector = mock[DataMigrationJsonConnector]
  private val actorSystem = mock[ActorSystem]
  private val messageApi = new DefaultMessagesApi(env, configuration, new DefaultLangs(configuration))
  private val appConfig = new AppConfig(configuration, env)
  private implicit val mat: Materializer = fakeApplication.materializer
  private val controller = new DataMigrationJsonController(
    new SuccessfulAuthenticatedAction, migrationService, migrationConnector, actorSystem, mat, messageApi, appConfig
  )

  private val csvList = List(
    "tblCaseClassMeth_csv", "historicCases_csv", "eBTI_Application_csv",
    "eBTI_Addresses_csv", "tblCaseRecord_csv", "tblCaseBTI_csv", "tblImages_csv",
    "tblCaseLMComments_csv", "tblMovement_csv")

  private val anonymizedCsvList = csvList.filterNot(_ == "historicCases_csv")

  val aSuccessfullyUploadedFile: FileUploaded = FileUploaded("name", "published", "text/plain", None, None)

  private def mockCsvField(row: Int, col: Int): String = s"$row-$col"
  private val mockCsvRowCount = 100
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

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(migrationService, migrationConnector)
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
      val data : MultipartFormData[TemporaryFile] = {
        val file = TemporaryFile(filename)
        val filePart = FilePart[TemporaryFile](key = "file", filename, contentType = Some(mimeType), ref = file)
        MultipartFormData[TemporaryFile](
          dataParts = Map("id" -> Seq(filename), "filename" -> Seq(filename), "mimetype" -> Seq(mimeType)),
          files = Seq(filePart),
          badParts = Seq.empty
        )
      }

      val result = await(controller.anonymiseData()(newFakeRequestWithCSRF.withBody(data)))

      status(result) shouldBe OK

    }

    "return valid anonymised files" in {
      anonymizedCsvList.foreach(filename => {
        val headers = (filename match {
          case _ if filename.contains("eBTI_Application") => "CaseNo,PreviousBTIRef,PreviousBTICountry,PreviousDateofValidity,PreviousNomenclature,PreviousNomenclatureCode,CustomsNomenclature,OtherNomenclature,EnvisagedImport,EnvisagedNomenclature,GoodsDescription,AdditionalInfo,Attachment,Samples,ReturnSamples,OtherApplications,Country1,Place1,DateOfApplication1,OtherBTIRef1,DateOfValidity1,OtherNomenclatureCode1,Country2,Place2,DateOfApplication2,OtherBTIRef2,DateOfValidity2,OtherNomenclatureCode2,OtherHolders,HolderCountry1,HolderOtherBTIRef1,HolderDateOfValidity1,HolderOtherNomenclatureCode1,HolderCountry2,HolderOtherBTIRef2,HolderDateOfValidity2,HolderOtherNomenclatureCode2,Contact,VATRegTurnNo,Reference,Created,Updated,Status,CancelledDate,CancelledUser,FastTrack,TransactionType,CustomsOther,LegalProceedingsPending,AdditionalInfo2,EnvisagedSpecialDesc,Signature,SignatureDate,CombinedNomenclature,TARICCode,TARICAdditionalCode1,TARICAdditionalCode2,NationalAdditionalCode,BrochureIncluded,PhotoIncluded,OtherIncluded"
          case _ if filename.contains("eBTI_Addresses") => "CaseNo,Name,Address1,Address2,Address3,Postcode,Country,TelephoneNo,FaxNo,CustomsID,Type,Reference,Created,Updated,City,Email"
          case _ if filename.contains("tblCaseRecord") => "CaseNo,InsCreatorID,InsCreationDate,InsCountryCode,InsGoodsID,InsBoardFileDatePrinted,InsBoardFilePrinted,InsBoardFileUserName,CaseName,CaseType,CaseFastTrack,CaseCategory,CaseBoardsFileNumber,CaseStatus,CaseDownLoaded,CaseSampleIndicator,CaseAppealIndicator,CaseReceiptDate,CaseClosedDate,CaseClosedReason,CaseInterimReplySentDate,CaseCompletedDate,CaseBoardFileRequestDate,CaseAddress1,CaseAddress2,CaseAddress3,CaseAddress4,CaseAddress5,CasePostCode,CaseTelephoneNo,CaseFaxNo,CaseAgentName,CaseCrossRefIndicator,CaseTeamCompleted,CaseUserCompleted,CaseNameCompleted,CaseSearchText,CaseReplacedBy,CaseReplacing,LiabilityPort,LiabilityPortOfficerName,LiabilityPortOfficerLoc,LiabilityPortOfficerTel,LiabilityEntryNo,LiabilityEntryDate,LiabilityStatus,AppealReceivedDate,AppealStatus,AppealTribunalDate,AppealCompletionDate,AppealResult,AppealResultDate,SupressDate,SupressTeam,SupressUserName,SupressReason,ElapsedDays,ElapsedDaysInterim,ApplicationRef,AppealDownloaded,CancelDownloaded,BTILetterPrintDate,CustAuthKey,CaseCustomsID,CaseEmail,ContactName"
          case _ if filename.contains("tblCaseBTI") => "CaseNo,StatusDate,Status,MISStatus,PrintedDate,StartValidityDate,ApplicationDate,Keywords1,Keywords2,Keywords3,Keywords4,Keywords5,Keywords6,Keywords7,Keywords8,Keywords9,Keywords10,Keywords11,Keywords12,Keywords13,Keywords14,Keywords15,Keywords16,Keywords17,Keywords18,Keywords19,Keywords20,EndValidityDate"
          case _ if filename.contains("tblCaseClassMeth") => "CaseNo,BERTISearch,EBTISearch,Justification,CommercialDenomenation,GoodsDescription,Exclusions,LGCExpertAdvice,OTCCommodityCode,ApplicantsCommodityCode,OTCImage,DescriptionIncluded,BrochureIncluded,PhotoIncluded,SampleIncluded,OtherIncluded,CombinedNomenclature,TARICCode,TARICAdditionalCode1,TARICAdditionalCode2,NationalAdditionalCode"
          case _ if filename.contains("tblImages") => "CaseNo,DateAdded,TimeAdded,Description,FileName,Counter,SendWithBTI,Confidential,SavedToFile,DeleteFlag,DeletedDate,DeletedTime,DeletingUserID,AddingUserID,IsApplicationAttachment,SendWithApp"
          case _ if filename.contains("tblMovement") => "CaseNo,DateSent,TimeSent,SenderID,SenderTeam,RecipientTeam,RecipientType,DateReceived,TimeReceived,RecipientID,Reason"
          case _ if filename.contains("tblCaseLMComments") => "CaseNo,Band7DateChecked,Band7TimeChecked,Band7Name,Band7User,Band7Satisfied,Band7Comments,Band9DateChecked,Band9TimeChecked,Band9Name,Band9User,Band9Satisfied,Band9Comments,Band11DateChecked,Band11TimeChecked,Band11Name,Band11User,Band11Satisfied,Band11Comments"
          case _ => throw new Exception("Incomplete test")
        }).split(",").toList
        val mimeType: String = "application/csv"
        val data: MultipartFormData[TemporaryFile] = {
          val file = TemporaryFile(filename)
          val writer = new BufferedWriter(new FileWriter(file.file))
          writer.write(mockCsv(headers))
          writer.close()
          val filePart = FilePart[TemporaryFile](key = "file", filename, contentType = Some(mimeType), ref = file)
          MultipartFormData[TemporaryFile](
            dataParts = Map("id" -> Seq(filename), "filename" -> Seq(filename), "mimetype" -> Seq(mimeType)),
            files = Seq(filePart),
            badParts = Seq.empty
          )
        }

        // Read the output file
        val result = await(controller.anonymiseData()(newFakeRequestWithCSRF.withBody(data)))
        val outputBody = await(result.body.consumeData)
        val outputLines = await(Source.single(outputBody)
          .via(lineScanner())
          .map(_.map(_.utf8String))
          .runWith(Sink.seq))

        val outputHeaders = outputLines.head
        val outputDataRows = outputLines.tail

        // Determine if a given field is anonymized (would be better if this could be obtained from a map)
        val isAnonymized = headers.map(header => (header, Anonymize.anonymize(filename, Map((header -> "TEST")))(header) != "TEST")).toMap

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
      })
    }

    "return 400" in {
      val data : MultipartFormData[TemporaryFile] = {
        MultipartFormData[TemporaryFile](
          dataParts = Map("id" -> Seq.empty, "filename" -> Seq.empty, "mimetype" -> Seq.empty),
          files = Seq.empty,
          badParts = Seq.empty
        )
      }

      val result = await(controller.anonymiseData()(newFakeRequestWithCSRF.withBody(data)))

      status(result) shouldBe BAD_REQUEST

    }
  }

  "postDataAndRedirect /" should {

    "return 300" in {
      given(migrationService.getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier]))
        .willReturn(Future.successful(List[FileUploaded](aSuccessfullyUploadedFile)))
      given(migrationConnector.sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier]))
        .willReturn(Future.successful(HttpResponse.apply(202)))

      val result: Result = await(controller.postDataAndRedirect()(newFakeRequestWithCSRF))

      status(result) shouldBe SEE_OTHER

      verify(migrationService, atLeastOnce()).getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce()).sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier])
    }

    "Handle 4xx Errors from service" in {
      when(migrationService.getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])).
        thenReturn(Future.failed(Upstream4xxResponse("error", 409, 0)))

      intercept[Upstream4xxResponse] {
        await(controller.postDataAndRedirect()(newFakeRequestWithCSRF))
      }

      verify(migrationService, atLeastOnce()).getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])
      verify(migrationConnector, never()).sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier])
    }

    "Handle 5xx Errors from connector" in {
      given(migrationService.getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier]))
        .willReturn(Future.successful(List[FileUploaded](aSuccessfullyUploadedFile)))
      given(migrationConnector.sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier]))
        .willReturn(Future.failed(Upstream5xxResponse("error", 500, 0)))

      intercept[Upstream5xxResponse] {
        await(controller.postDataAndRedirect()(newFakeRequestWithCSRF))
      }

      verify(migrationService, atLeastOnce()).getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce()).sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier])
    }

    "Handle unknown Errors from connector" in {
      given(migrationService.getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier]))
        .willReturn(Future.successful(List[FileUploaded](aSuccessfullyUploadedFile)))
      given(migrationConnector.sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier]))
        .willReturn(Future.failed(new RuntimeException("error")))

      given(migrationConnector.sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier]))
        .willReturn(Future.successful(HttpResponse.apply(BAD_REQUEST)))

      intercept[RuntimeException] {
        await(controller.postDataAndRedirect()(newFakeRequestWithCSRF))
      }

      verify(migrationService, atLeastOnce()).getDataMigrationFilesDetails(refEq(csvList))(any[HeaderCarrier])
      verify(migrationConnector, atLeastOnce()).sendDataForProcessing(refEq(List(aSuccessfullyUploadedFile)))(any[HeaderCarrier])
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
        .willReturn(Future.successful(HttpResponse.apply(200, responseJson= Some(Json.obj("status" -> "inserting")))))

      val result: Result = await(controller.getStatusOfJsonProcessing()(newFakeRequestWithCSRF))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.obj("status" -> "inserting")
    }

    "return 400" in {
      given(migrationConnector.getStatusOfJsonProcessing(any[HeaderCarrier]))
        .willReturn(Future.successful(HttpResponse.apply(400, responseJson= Some(Json.obj("error" -> "error while inserting")))))

      val result: Result = await(controller.getStatusOfJsonProcessing()(newFakeRequestWithCSRF))

      status(result) shouldBe BAD_REQUEST
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

      val response = StreamedResponse.apply(
        DefaultWSResponseHeaders(200, Map.empty), body= Source.apply(List(ByteString(json.toString()))))
      given(migrationConnector.downloadBTIJson).willReturn(Future.successful(response))

      val result = await(controller.downloadBTIJson()(newFakeRequestWithCSRF))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.parse("""{
                                               |  "href": "url",
                                               |  "fields": {
                                               |    "field": "value"
                                               |  }
                                               |}""".stripMargin)

    }

    "return 400" in {
      val response = StreamedResponse.apply(
        DefaultWSResponseHeaders(400, Map.empty), body= Source.apply(
          List(ByteString(Json.obj("error" -> "error while building josn").toString()))))
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

      val response = StreamedResponse.apply(
        DefaultWSResponseHeaders(200, Map.empty), body= Source.apply(List(ByteString(json.toString()))))
      given(migrationConnector.downloadLiabilitiesJson).willReturn(Future.successful(response))

      val result = await(controller.downloadLiabilitiesJson()(newFakeRequestWithCSRF))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.parse("""{
                                               |  "href": "url",
                                               |  "fields": {
                                               |    "field": "value"
                                               |  }
                                               |}""".stripMargin)

    }

    "return 400" in {
      val response = StreamedResponse.apply(
        DefaultWSResponseHeaders(400, Map.empty), body= Source.apply(
          List(ByteString(Json.obj("error" -> "error while building josn").toString()))))
      given(migrationConnector.downloadLiabilitiesJson).willReturn(Future.successful(response))

      intercept[BadRequestException](
        await(controller.downloadLiabilitiesJson()(newFakeRequestWithCSRF))
      )
    }
  }

  private def newFakeRequestWithCSRF: FakeRequest[AnyContentAsEmpty.type] = {
    val tokenProvider: TokenProvider = fakeApplication.injector.instanceOf[TokenProvider]
    val csrfTags = Map(Token.NameRequestTag -> "csrfToken", Token.RequestTag -> tokenProvider.generateToken)
    FakeRequest("GET", "/", FakeHeaders(), AnyContentAsEmpty, tags = csrfTags)
  }

}
