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
    Anonymize.anonymize("eBTI_Application", Map("CaseName" -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("eBTI_Addresses", Map("CaseName" -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("tblCaseRecord", Map("CaseName" -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("tblCaseBTI", Map("CaseName" -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("tblCaseClassMeth", Map("CaseName" -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("tblCaseLMComments", Map("CaseName" -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("tblImages", Map("CaseName" -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("tblSample", Map("CaseName" -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("tblUser", Map("CaseName" -> "")) shouldBe Map("CaseName" -> "")
    Anonymize.anonymize("tblMovement", Map("CaseName" -> "")) shouldBe Map("CaseName" -> "")
  }

  it should "anonymize eBTI_Application" in {
    Anonymize.anonymize("eBTI_Application", Map("PreviousBTICountry" -> "Yoda")) shouldNot contain("PreviousBTICountry" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("GoodsDescription" -> "Yoda")) shouldNot contain("GoodsDescription" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("AdditionalInfo" -> "Yoda")) shouldNot contain("AdditionalInfo" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("AdditionalInfo2" -> "Yoda")) shouldNot contain("AdditionalInfo2" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("Country1" -> "Yoda")) shouldNot contain("Country1" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("Place1" -> "Yoda")) shouldNot contain("Place1" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("Country2" -> "Yoda")) shouldNot contain("Country2" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("Place2" -> "Yoda")) shouldNot contain("Place2" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("HolderCountry1" -> "Yoda")) shouldNot contain("HolderCountry1" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("HolderCountry2" -> "Yoda")) shouldNot contain("HolderCountry2" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("Contact" -> "Yoda")) shouldNot contain("Contact" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("VATRegTurnNo" -> "Yoda")) shouldNot contain("VATRegTurnNo" -> "Yoda")
    Anonymize.anonymize("eBTI_Application", Map("EnvisagedSpecialDesc" -> "Yoda")) shouldNot contain("EnvisagedSpecialDesc" -> "Yoda")
  }

  it should "not anonymize eBTI_Application fields that contain no PII" in {
    Anonymize.anonymize("eBTI_Application", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize eBTI_Addresses" in {
    Anonymize.anonymize("eBTI_Addresses", Map("Name" -> "Yoda")) shouldNot contain("Name" -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("Address1" -> "Yoda")) shouldNot contain("Address1" -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("Address2" -> "Yoda")) shouldNot contain("Address2" -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("Address3" -> "Yoda")) shouldNot contain("Address3" -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("City" -> "Yoda")) shouldNot contain("City" -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("Postcode" -> "Yoda")) shouldNot contain("Postcode" -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("Country" -> "Yoda")) shouldNot contain("Country" -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("TelephoneNo" -> "Yoda")) shouldNot contain("TelephoneNo" -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("FaxNo" -> "Yoda")) shouldNot contain("FaxNo" -> "Yoda")
    Anonymize.anonymize("eBTI_Addresses", Map("Email" -> "Yoda")) shouldNot contain("Email" -> "Yoda")
  }

  it should "not anonymize eBTI_Addresses fields that contain no PII" in {
    Anonymize.anonymize("eBTI_Addresses", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize tblCaseRecord" in {
    Anonymize.anonymize("tblCaseRecord", Map("CaseName" -> "Yoda")) shouldNot contain("CaseName" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("InsBoardFileUserName" -> "Yoda")) shouldNot contain("InsBoardFileUserName" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseClosedReason" -> "Yoda")) shouldNot contain("CaseClosedReason" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseAddress1" -> "Yoda")) shouldNot contain("CaseAddress1" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseAddress2" -> "Yoda")) shouldNot contain("CaseAddress2" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseAddress3" -> "Yoda")) shouldNot contain("CaseAddress3" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseAddress4" -> "Yoda")) shouldNot contain("CaseAddress4" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseAddress5" -> "Yoda")) shouldNot contain("CaseAddress5" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CasePostCode" -> "Yoda")) shouldNot contain("CasePostCode" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseTelephoneNo" -> "Yoda")) shouldNot contain("CaseTelephoneNo" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseFaxNo" -> "Yoda")) shouldNot contain("CaseFaxNo" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseAgentName" -> "Yoda")) shouldNot contain("CaseAgentName" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("LiabilityPortOfficerName" -> "Yoda")) shouldNot contain("LiabilityPortOfficerName" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("LiabilityPortOfficerTel" -> "Yoda")) shouldNot contain("LiabilityPortOfficerTel" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("SuppressUserName" -> "Yoda")) shouldNot contain("SuppressUserName" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("SuppressReason" -> "Yoda")) shouldNot contain("SuppressReason" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("CaseEmail" -> "Yoda")) shouldNot contain("CaseEmail" -> "Yoda")
    Anonymize.anonymize("tblCaseRecord", Map("ContactName" -> "Yoda")) shouldNot contain("ContactName" -> "Yoda")
  }

  it should "not anonymize tblCaseRecord fields that contain no PII" in {
    Anonymize.anonymize("tblCaseRecord", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize tblCaseBTI" in {
    Anonymize.anonymize("tblCaseBTI", Map("Keywords1" -> "Yoda")) shouldNot contain("Keywords1" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords2" -> "Yoda")) shouldNot contain("Keywords2" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords3" -> "Yoda")) shouldNot contain("Keywords3" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords4" -> "Yoda")) shouldNot contain("Keywords4" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords5" -> "Yoda")) shouldNot contain("Keywords5" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords6" -> "Yoda")) shouldNot contain("Keywords6" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords7" -> "Yoda")) shouldNot contain("Keywords7" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords8" -> "Yoda")) shouldNot contain("Keywords8" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords9" -> "Yoda")) shouldNot contain("Keywords9" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords10" -> "Yoda")) shouldNot contain("Keywords10" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords11" -> "Yoda")) shouldNot contain("Keywords11" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords12" -> "Yoda")) shouldNot contain("Keywords12" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords13" -> "Yoda")) shouldNot contain("Keywords13" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords14" -> "Yoda")) shouldNot contain("Keywords14" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords15" -> "Yoda")) shouldNot contain("Keywords15" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords16" -> "Yoda")) shouldNot contain("Keywords16" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords17" -> "Yoda")) shouldNot contain("Keywords17" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords18" -> "Yoda")) shouldNot contain("Keywords18" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords19" -> "Yoda")) shouldNot contain("Keywords19" -> "Yoda")
    Anonymize.anonymize("tblCaseBTI", Map("Keywords20" -> "Yoda")) shouldNot contain("Keywords20" -> "Yoda")
  }

  it should "not anonymize tblCaseBTI fields that contain no PII" in {
    Anonymize.anonymize("tblCaseBTI", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize tblCaseClassMeth" in {
    Anonymize.anonymize("tblCaseClassMeth", Map("MethodSearch" -> "Yoda")) shouldNot contain("MethodSearch" -> "Yoda")
    Anonymize.anonymize("tblCaseClassMeth", Map("Justification" -> "Yoda")) shouldNot contain("Justification" -> "Yoda")
    Anonymize.anonymize("tblCaseClassMeth", Map("GoodsDescription" -> "Yoda")) shouldNot contain("GoodsDescription" -> "Yoda")
    Anonymize.anonymize("tblCaseClassMeth", Map("LGCExpertAdvice" -> "Yoda")) shouldNot contain("LGCExpertAdvice" -> "Yoda")
  }

  it should "not anonymize tblCaseClassMeth fields that contain no PII" in {
    Anonymize.anonymize("tblCaseClassMeth", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize tblCaseLMComments" in {
    Anonymize.anonymize("tblCaseLMComments", Map("Band7Name" -> "Yoda")) shouldNot contain("Band7Name" -> "Yoda")
    Anonymize.anonymize("tblCaseLMComments", Map("Band7Comments" -> "Yoda")) shouldNot contain("Band7Comments" -> "Yoda")
    Anonymize.anonymize("tblCaseLMComments", Map("Band9Name" -> "Yoda")) shouldNot contain("Band9Name" -> "Yoda")
    Anonymize.anonymize("tblCaseLMComments", Map("Band9Comments" -> "Yoda")) shouldNot contain("Band9Comments" -> "Yoda")
    Anonymize.anonymize("tblCaseLMComments", Map("Band11Name" -> "Yoda")) shouldNot contain("Band11Name" -> "Yoda")
    Anonymize.anonymize("tblCaseLMComments", Map("Band11Comments" -> "Yoda")) shouldNot contain("Band11Comments" -> "Yoda")
  }

  it should "not anonymize tblCaseLMComments fields that contain no PII" in {
    Anonymize.anonymize("tblCaseLMComments", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }
  
  it should "anonymize tblImages" in {
    Anonymize.anonymize("tblImages", Map("Description" -> "Yoda")) shouldNot contain("Description" -> "Yoda")
    Anonymize.anonymize("tblImages", Map("FileName" -> "Yoda")) shouldNot contain("FileName" -> "Yoda")
  }

  it should "not anonymize tblImages fields that contain no PII" in {
    Anonymize.anonymize("tblImages", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize tblMovement" in {
    Anonymize.anonymize("tblMovement", Map("Reason" -> "Yoda")) shouldNot contain("Reason" -> "Yoda")
  }

  it should "not anonymize tblMovement fields that contain no PII" in {
    Anonymize.anonymize("tblMovement", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize tblSample" in {
    Anonymize.anonymize("tblSample", Map("Action" -> "Yoda")) shouldNot contain("Action" -> "Yoda")
  }

  it should "not anonymize tblSample fields that contain no PII" in {
    Anonymize.anonymize("tblSample", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }

  it should "anonymize tblUser" in {
    Anonymize.anonymize("tblUser", Map("FirstName" -> "Yoda")) shouldNot contain("FirstName" -> "Yoda")
    Anonymize.anonymize("tblUser", Map("LastName" -> "Yoda")) shouldNot contain("LastName" -> "Yoda")
    Anonymize.anonymize("tblUser", Map("Extension" -> "Yoda")) shouldNot contain("Extension" -> "Yoda")
    Anonymize.anonymize("tblUser", Map("Email" -> "Yoda")) shouldNot contain("Email" -> "Yoda")
  }

  it should "not anonymize tblUser fields that contain no PII" in {
    Anonymize.anonymize("tblUser", Map("CaseNo" -> "123456")) should contain("CaseNo" -> "123456")
  }
}
