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

package uk.gov.hmrc.bindingtariffadminfrontend.model

import org.scalatest._

class AnonymizeSpec extends FlatSpec with Matchers {
  "Anonymize.anonymize" should "throw an exception for an unknown table name" in {
    assertThrows[AnonymizationFailedException] {
      Anonymize.anonymize("foo", Map("CaseName" -> "Hulk Hogan's Floral Wreaths", "AgentName" -> "Batman"))
    }
  }

  it should "ignore blank data" in {
    Anonymize.anonymize("eBTI_Application", Map("CaseName"  -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("eBTI_Addresses", Map("CaseName"    -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("tblCaseRecord", Map("CaseName"     -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("tblCaseBTI", Map("CaseName"        -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("tblCaseClassMeth", Map("CaseName"  -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("tblCaseLMComments", Map("CaseName" -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("tblImages", Map("CaseName"         -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("tblMovement", Map("CaseName"       -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("Legal_Proceedings", Map("CaseName" -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("TblCaseMiscCorres", Map("CaseName" -> "")) shouldBe Map("CaseName" -> "")
  }

  it should "ignore NULL fields" in {
    Anonymize.anonymize("eBTI_Application", Map("CaseName"  -> "NULL")) shouldBe Map("CaseName" -> "NULL")
    Anonymize.anonymize("eBTI_Addresses", Map("CaseName"    -> "NULL")) shouldBe Map("CaseName" -> "NULL")
    Anonymize.anonymize("tblCaseRecord", Map("CaseName"     -> "NULL")) shouldBe Map("CaseName" -> "NULL")
    Anonymize.anonymize("tblCaseBTI", Map("CaseName"        -> "NULL")) shouldBe Map("CaseName" -> "NULL")
    Anonymize.anonymize("tblCaseClassMeth", Map("CaseName"  -> "NULL")) shouldBe Map("CaseName" -> "NULL")
    Anonymize.anonymize("tblCaseLMComments", Map("CaseName" -> "NULL")) shouldBe Map("CaseName" -> "NULL")
    Anonymize.anonymize("tblImages", Map("CaseName"         -> "NULL")) shouldBe Map("CaseName" -> "NULL")
    Anonymize.anonymize("tblMovement", Map("CaseName"       -> "NULL")) shouldBe Map("CaseName" -> "NULL")
    Anonymize.anonymize("Legal_Proceedings", Map("CaseName" -> "NULL")) shouldBe Map("CaseName" -> "NULL")
    Anonymize.anonymize("TblCaseMiscCorres", Map("CaseName" -> "NULL")) shouldBe Map("CaseName" -> "NULL")
  }

  it should "anonymize eBTI_Application" in {
    Anonymize.anonymize("eBTI_Application", Map("PreviousBTIRef" -> "Yoda")) shouldNot contain(
      "PreviousBTIRef" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("PreviousBTICountry" -> "Yoda")) shouldNot contain(
      "PreviousBTICountry" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("PreviousDateofValidity" -> "Yoda")) shouldNot contain(
      "PreviousDateofValidity" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("PreviousNomenclatureCode" -> "Yoda")) shouldNot contain(
      "PreviousNomenclatureCode" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("EnvisagedNomenclature" -> "Yoda")) shouldNot contain(
      "EnvisagedNomenclature" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("GoodsDescription" -> "Yoda")) shouldNot contain(
      "GoodsDescription" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("AdditionalInfo" -> "Yoda")) shouldNot contain(
      "AdditionalInfo" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("AdditionalInfo2" -> "Yoda")) shouldNot contain(
      "AdditionalInfo2" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("Country1"           -> "Yoda")) shouldNot contain("Country1" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("Place1"             -> "Yoda")) shouldNot contain("Place1" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("DateOfApplication1" -> "Yoda")) shouldNot contain(
      "DateOfApplication1" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("OtherBTIRef1"    -> "Yoda")) shouldNot contain("OtherBTIRef1" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("DateOfValidity1" -> "Yoda")) shouldNot contain(
      "DateOfValidity1" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("OtherNomenclatureCode1" -> "Yoda")) shouldNot contain(
      "OtherNomenclatureCode1" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("Country2"           -> "Yoda")) shouldNot contain("Country2" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("Place2"             -> "Yoda")) shouldNot contain("Place2" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("DateOfApplication2" -> "Yoda")) shouldNot contain(
      "DateOfApplication2" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("OtherBTIRef2"    -> "Yoda")) shouldNot contain("OtherBTIRef2" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("DateOfValidity2" -> "Yoda")) shouldNot contain(
      "DateOfValidity2" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("OtherNomenclatureCode2" -> "Yoda")) shouldNot contain(
      "OtherNomenclatureCode2" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("HolderCountry1" -> "Yoda")) shouldNot contain(
      "HolderCountry1" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("HolderOtherBTIRef1" -> "Yoda")) shouldNot contain(
      "HolderOtherBTIRef1" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("HolderDateOfValidity1" -> "Yoda")) shouldNot contain(
      "HolderDateOfValidity1" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("HolderOtherNomenclatureCode1" -> "Yoda")) shouldNot contain(
      "HolderOtherNomenclatureCode1" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("HolderCountry2" -> "Yoda")) shouldNot contain(
      "HolderCountry2" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("HolderOtherBTIRef2" -> "Yoda")) shouldNot contain(
      "HolderOtherBTIRef2" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("HolderDateOfValidity2" -> "Yoda")) shouldNot contain(
      "HolderDateOfValidity2" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("HolderOtherNomenclatureCode2" -> "Yoda")) shouldNot contain(
      "HolderOtherNomenclatureCode2" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("Contact"              -> "Yoda")) shouldNot contain("Contact" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("VATRegTurnNo"         -> "Yoda")) shouldNot contain("VATRegTurnNo" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("CustomsOther"         -> "Yoda")) shouldNot contain("CustomsOther" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("EnvisagedSpecialDesc" -> "Yoda")) shouldNot contain(
      "EnvisagedSpecialDesc" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("Signature"            -> "Yoda")) shouldNot contain("Signature" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("SignatureDate"        -> "Yoda")) shouldNot contain("SignatureDate" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("CombinedNomenclature" -> "Yoda")) shouldNot contain(
      "CombinedNomenclature" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("TARICCode"            -> "Yoda")) shouldNot contain("TARICCode" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("TARICAdditionalCode1" -> "Yoda")) shouldNot contain(
      "TARICAdditionalCode1" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("TARICAdditionalCode2" -> "Yoda")) shouldNot contain(
      "TARICAdditionalCode2" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("NationalAdditionalCode" -> "Yoda")) shouldNot contain(
      "NationalAdditionalCode" -> "Yoda"
    )
    Anonymize.anonymize("eBTI_Application", Map("OtherNomenclature" -> "Yoda")) shouldNot contain(
      "OtherNomenclature" -> "Yoda"
    )
  }

  it should "not anonymize eBTI_Application fields that contain no PII" in {
    Anonymize.anonymize("eBTI_Application", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize eBTI_Addresses" in {
    Anonymize.anonymize("eBTI_Addresses", Map("Name"        -> "Yoda")) shouldNot contain("Name"        -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("Address1"    -> "Yoda")) shouldNot contain("Address1"    -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("Address2"    -> "Yoda")) shouldNot contain("Address2"    -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("Address3"    -> "Yoda")) shouldNot contain("Address3"    -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("Postcode"    -> "Yoda")) shouldNot contain("Postcode"    -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("Country"     -> "Yoda")) shouldNot contain("Country"     -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("TelephoneNo" -> "Yoda")) shouldNot contain("TelephoneNo" -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("FaxNo"       -> "Yoda")) shouldNot contain("FaxNo"       -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("CustomsID"   -> "Yoda")) shouldNot contain("CustomsID"   -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("Reference"   -> "Yoda")) shouldNot contain("Reference"   -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("City"        -> "Yoda")) shouldNot contain("City"        -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("Email"       -> "Yoda")) shouldNot contain("Email"       -> "Yoda")
  }

  it should "not anonymize eBTI_Addresses fields that contain no PII" in {
    Anonymize.anonymize("eBTI_Addresses", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize tblCaseRecord" in {
    Anonymize.anonymize("tblCaseRecord", Map("InsCountryCode"       -> "Yoda")) shouldNot contain("InsCountryCode" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("InsGoodsID"           -> "Yoda")) shouldNot contain("InsGoodsID" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("InsBoardFileUserName" -> "Yoda")) shouldNot contain(
      "InsBoardFileUserName" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("InsBoardFileDatePrinted" -> "Yoda")) shouldNot contain(
      "InsBoardFileDatePrinted" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("CaseName"             -> "Yoda")) shouldNot contain("CaseName" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseBoardsFileNumber" -> "Yoda")) shouldNot contain(
      "CaseBoardsFileNumber" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("CaseInterimReplySentDate" -> "Yoda")) shouldNot contain(
      "CaseInterimReplySentDate" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("CaseBoardFileRequestDate" -> "Yoda")) shouldNot contain(
      "CaseBoardFileRequestDate" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("CaseAddress1"    -> "Yoda")) shouldNot contain("CaseAddress1" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseAddress2"    -> "Yoda")) shouldNot contain("CaseAddress2" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseAddress3"    -> "Yoda")) shouldNot contain("CaseAddress3" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseAddress4"    -> "Yoda")) shouldNot contain("CaseAddress4" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseAddress5"    -> "Yoda")) shouldNot contain("CaseAddress5" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CasePostCode"    -> "Yoda")) shouldNot contain("CasePostCode" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseTelephoneNo" -> "Yoda")) shouldNot contain(
      "CaseTelephoneNo" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("CaseFaxNo"         -> "Yoda")) shouldNot contain("CaseFaxNo" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseAgentName"     -> "Yoda")) shouldNot contain("CaseAgentName" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseNameCompleted" -> "Yoda")) shouldNot contain(
      "CaseNameCompleted" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("CaseSearchText"           -> "Yoda")) shouldNot contain("CaseSearchText" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseReplacedBy"           -> "Yoda")) shouldNot contain("CaseReplacedBy" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseReplacing"            -> "Yoda")) shouldNot contain("CaseReplacing" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("LiabilityPort"            -> "Yoda")) shouldNot contain("LiabilityPort" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("LiabilityPortOfficerName" -> "Yoda")) shouldNot contain(
      "LiabilityPortOfficerName" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("LiabilityPortOfficerLoc" -> "Yoda")) shouldNot contain(
      "LiabilityPortOfficerLoc" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("LiabilityPortOfficerTel" -> "Yoda")) shouldNot contain(
      "LiabilityPortOfficerTel" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("LiabilityEntryNo" -> "Yoda")) shouldNot contain(
      "LiabilityEntryNo" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("SupressDate"     -> "Yoda")) shouldNot contain("SupressDate" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("SupressTeam"     -> "Yoda")) shouldNot contain("SupressTeam" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("SupressUserName" -> "Yoda")) shouldNot contain(
      "SupressUserName" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("SupressReason"      -> "Yoda")) shouldNot contain("SupressReason" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("ElapsedDaysInterim" -> "Yoda")) shouldNot contain(
      "ElapsedDaysInterim" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("ApplicationRef"   -> "Yoda")) shouldNot contain("ApplicationRef" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("AppealDownloaded" -> "Yoda")) shouldNot contain(
      "AppealDownloaded" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("CancelDownloaded" -> "Yoda")) shouldNot contain(
      "CancelDownloaded" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("BTILetterPrintDate" -> "Yoda")) shouldNot contain(
      "BTILetterPrintDate" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseRecord", Map("CustAuthKey"   -> "Yoda")) shouldNot contain("CustAuthKey"   -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseCustomsID" -> "Yoda")) shouldNot contain("CaseCustomsID" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseEmail"     -> "Yoda")) shouldNot contain("CaseEmail"     -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("ContactName"   -> "Yoda")) shouldNot contain("ContactName"   -> "Yoda")
  }

  it should "not anonymize tblCaseRecord fields that contain no PII" in {
    Anonymize.anonymize("tblCaseRecord", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize tblCaseBTI" in {
    Anonymize.anonymize("tblCaseBTI", Map("Keywords1"       -> "Yoda")) shouldNot contain("Keywords1"       -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords2"       -> "Yoda")) shouldNot contain("Keywords2"       -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords3"       -> "Yoda")) shouldNot contain("Keywords3"       -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords4"       -> "Yoda")) shouldNot contain("Keywords4"       -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords5"       -> "Yoda")) shouldNot contain("Keywords5"       -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords6"       -> "Yoda")) shouldNot contain("Keywords6"       -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords7"       -> "Yoda")) shouldNot contain("Keywords7"       -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords8"       -> "Yoda")) shouldNot contain("Keywords8"       -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords9"       -> "Yoda")) shouldNot contain("Keywords9"       -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords10"      -> "Yoda")) shouldNot contain("Keywords10"      -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords11"      -> "Yoda")) shouldNot contain("Keywords11"      -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords12"      -> "Yoda")) shouldNot contain("Keywords12"      -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords13"      -> "Yoda")) shouldNot contain("Keywords13"      -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords14"      -> "Yoda")) shouldNot contain("Keywords14"      -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords15"      -> "Yoda")) shouldNot contain("Keywords15"      -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords16"      -> "Yoda")) shouldNot contain("Keywords16"      -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords17"      -> "Yoda")) shouldNot contain("Keywords17"      -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords18"      -> "Yoda")) shouldNot contain("Keywords18"      -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords19"      -> "Yoda")) shouldNot contain("Keywords19"      -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords20"      -> "Yoda")) shouldNot contain("Keywords20"      -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("ApplicationDate" -> "Yoda")) shouldNot contain("ApplicationDate" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("PrintedDate"     -> "Yoda")) shouldNot contain("PrintedDate"     -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("StatusDate"      -> "Yoda")) shouldNot contain("StatusDate"      -> "Yoda")
  }

  it should "not anonymize tblCaseBTI fields that contain no PII" in {
    Anonymize.anonymize("tblCaseBTI", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize tblCaseClassMeth" in {
    Anonymize.anonymize("tblCaseClassMeth", Map("MethodSearch"     -> "Yoda")) shouldNot contain("MethodSearch" -> "Yoda")
    Anonymize.anonymize("tblCaseClassMeth", Map("Justification"    -> "Yoda")) shouldNot contain("Justification" -> "Yoda")
    Anonymize.anonymize("tblCaseClassMeth", Map("GoodsDescription" -> "Yoda")) shouldNot contain(
      "GoodsDescription" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseClassMeth", Map("NationalAdditionalCode" -> "Yoda")) shouldNot contain(
      "NationalAdditionalCode" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseClassMeth", Map("TARICAdditionalCode2" -> "Yoda")) shouldNot contain(
      "TARICAdditionalCode2" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseClassMeth", Map("TARICAdditionalCode1" -> "Yoda")) shouldNot contain(
      "TARICAdditionalCode1" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseClassMeth", Map("TARICCode"            -> "Yoda")) shouldNot contain("TARICCode" -> "Yoda")
    Anonymize.anonymize("tblCaseClassMeth", Map("CombinedNomenclature" -> "Yoda")) shouldNot contain(
      "CombinedNomenclature" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseClassMeth", Map("ApplicantsCommodityCode" -> "Yoda")) shouldNot contain(
      "ApplicantsCommodityCode" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseClassMeth", Map("OTCCommodityCode" -> "Yoda")) shouldNot contain(
      "OTCCommodityCode" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseClassMeth", Map("Exclusions"             -> "Yoda")) shouldNot contain("Exclusions" -> "Yoda")
    Anonymize.anonymize("tblCaseClassMeth", Map("CommercialDenomenation" -> "Yoda")) shouldNot contain(
      "CommercialDenomenation" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseClassMeth", Map("EBTISearch"  -> "Yoda")) shouldNot contain("EBTISearch"  -> "Yoda")
    Anonymize.anonymize("tblCaseClassMeth", Map("BERTISearch" -> "Yoda")) shouldNot contain("BERTISearch" -> "Yoda")
  }

  it should "not anonymize tblCaseClassMeth fields that contain no PII" in {
    Anonymize.anonymize("tblCaseClassMeth", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize tblCaseLMComments" in {
    Anonymize.anonymize("tblCaseLMComments", Map("Band7Name"     -> "Yoda")) shouldNot contain("Band7Name" -> "Yoda")
    Anonymize.anonymize("tblCaseLMComments", Map("Band7Comments" -> "Yoda")) shouldNot contain(
      "Band7Comments" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseLMComments", Map("Band9Name"     -> "Yoda")) shouldNot contain("Band9Name" -> "Yoda")
    Anonymize.anonymize("tblCaseLMComments", Map("Band9Comments" -> "Yoda")) shouldNot contain(
      "Band9Comments" -> "Yoda"
    )
    Anonymize.anonymize("tblCaseLMComments", Map("Band11Name"     -> "Yoda")) shouldNot contain("Band11Name" -> "Yoda")
    Anonymize.anonymize("tblCaseLMComments", Map("Band11Comments" -> "Yoda")) shouldNot contain(
      "Band11Comments" -> "Yoda"
    )
  }

  it should "not anonymize tblCaseLMComments fields that contain no PII" in {
    Anonymize.anonymize("tblCaseLMComments", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize tblImages" in {
    Anonymize.anonymize("tblImages", Map("Description"    -> "Yoda")) shouldNot contain("Description"    -> "Yoda")
    Anonymize.anonymize("tblImages", Map("DeletingUserID" -> "Yoda")) shouldNot contain("DeletingUserID" -> "Yoda")
    Anonymize.anonymize("tblImages", Map("DeletedTime"    -> "Yoda")) shouldNot contain("DeletedTime"    -> "Yoda")
    Anonymize.anonymize("tblImages", Map("DeletedDate"    -> "Yoda")) shouldNot contain("DeletedDate"    -> "Yoda")
  }

  it should "not anonymize tblImages fields that contain no PII" in {
    Anonymize.anonymize("tblImages", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize tblMovement" in {
    Anonymize.anonymize("tblMovement", Map("RecipientType" -> "Yoda")) shouldNot contain("RecipientType" -> "Yoda")
    Anonymize.anonymize("tblMovement", Map("Reason"        -> "Yoda")) shouldNot contain("Reason"        -> "Yoda")
  }

  it should "not anonymize tblMovement fields that contain no PII" in {
    Anonymize.anonymize("tblMovement", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize Legal_Proceeding" in {
    Anonymize.anonymize("Legal_Proceedings", Map("CourtName"       -> "Yoda")) shouldNot contain("CourtName" -> "Yoda")
    Anonymize.anonymize("Legal_Proceedings", Map("StreetAndNumber" -> "Yoda")) shouldNot contain(
      "StreetAndNumber" -> "Yoda"
    )
    Anonymize.anonymize("Legal_Proceedings", Map("City"           -> "Yoda")) shouldNot contain("City" -> "Yoda")
    Anonymize.anonymize("Legal_Proceedings", Map("Postcode"       -> "Yoda")) shouldNot contain("Postcode" -> "Yoda")
    Anonymize.anonymize("Legal_Proceedings", Map("Country"        -> "Yoda")) shouldNot contain("Country" -> "Yoda")
    Anonymize.anonymize("Legal_Proceedings", Map("CourtCaseRefNo" -> "Yoda")) shouldNot contain(
      "CourtCaseRefNo" -> "Yoda"
    )
  }

  it should "not anonymize Legal_Proceeding fields that contain no PII" in {
    Anonymize.anonymize("Legal_Proceedings", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize TblCaseMiscCorres" in {
    Anonymize.anonymize("TblCaseMiscCorres", Map("Comments" -> "Yoda")) shouldNot contain("Comments" -> "Yoda")
  }

  it should "not anonymize TblCaseMiscCorres fields that contain no PII" in {
    Anonymize.anonymize("TblCaseMiscCorres", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }
}
