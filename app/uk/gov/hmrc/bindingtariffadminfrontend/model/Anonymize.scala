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

import com.github.javafaker._
import java.{util => ju}

object Anonymize {
  private val faker = new Faker(ju.Locale.UK)

  val anonymized: String = "???"
  def anonymizing: Any => String = _ => anonymized

  def anonymize(tblName: String, data: Map[String, String]): Map[String, String] = tblName match {
    case _ if tblName.contains("eBTI_Application") =>
      anonymizeEBTIApplication(data)
    case _ if tblName.contains("eBTI_Addresses") =>
      anonymizeEBTIAddresses(data)
    case _ if tblName.contains("tblCaseRecord") =>
      anonymizeTblCaseRecord(data)
    case _ if tblName.contains("tblCaseBTI") =>
      anonymizeTblCaseBTI(data)
    case _ if tblName.contains("tblCaseClassMeth") =>
      anonymizeTblCaseClassMeth(data)
    case _ if tblName.contains("tblImages") =>
      anonymizeTblImages(data)
    case _ if tblName.contains("tblMovement") =>
      anonymizeTblMovement(data)
    case _ if tblName.contains("tblSample") =>
      anonymizeTblSample(data)
    case _ if tblName.contains("tblUser") =>
      anonymizeTblUser(data)
    case _ if tblName.contains("tblCaseLMComments") =>
      anonymizeTblCaseLMComments(data)
    case _ =>
      throw new AnonymizationFailedException(s"The file name ${tblName} was not recognised")
  }

  private def anonymizeEBTIApplication(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k @ "PreviousBTICountry", _) => (k, faker.country().name())
    case (k @ "GoodsDescription", _) => (k, faker.lorem().paragraph())
    case (k @ "AdditionalInfo", _) => (k, faker.lorem().paragraph())
    case (k @ "AdditionalInfo2", _) => (k, faker.lorem().paragraph())
    case (k @ "Country1", _) => (k, faker.country().name())
    case (k @ "Place1", _) => (k, faker.country().capital())
    case (k @ "Country2", _) => (k, faker.country().name())
    case (k @ "Place2", _) => (k, faker.country().capital())
    case (k @ "HolderCountry1", _) => (k, faker.country().name())
    case (k @ "HolderCountry2", _) => (k, faker.country().name())
    case (k @ "Contact", _) => (k, faker.name().fullName())
    case (k @ "VATRegTurnNo", _) => (k, faker.number().randomNumber().toString())
    case (k @ "EnvisagedSpecialDesc", _) => (k, faker.lorem().paragraph())
    case other => other
  }

  private def anonymizeEBTIAddresses(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k @ "Name", _) => (k, faker.name.fullName())
    case (k @ "Address1", _) => (k, faker.address().buildingNumber())
    case (k @ "Address2", _) => (k, faker.address().streetAddress())
    case (k @ "Address3", _) => (k, faker.address().state())
    case (k @ "City", _) => (k, faker.address().city())
    case (k @ "Postcode", _) => (k, faker.address().zipCode())
    case (k @ "Country", _) => (k, faker.address().country())
    case (k @ "TelephoneNo", _) => (k, faker.phoneNumber().phoneNumber())
    case (k @ "FaxNo", _) => (k, faker.phoneNumber().phoneNumber())
    case (k @ "Email", _) => (k, faker.internet().emailAddress())
    case other => other
  }

  private def anonymizeTblCaseRecord(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k @ "InsBoardFileUserName", _) => (k, faker.name().fullName())
    case (k @ "CaseName", _) => (k, faker.name().fullName())
    case (k @ "CaseClosedReason", _) => (k, faker.lorem().sentence())
    case (k @ "CaseAddress1", _) => (k, faker.address().buildingNumber())
    case (k @ "CaseAddress2", _) => (k, faker.address().streetAddress())
    case (k @ "CaseAddress3", _) => (k, faker.address().city())
    case (k @ "CaseAddress4", _) => (k, faker.address().state())
    case (k @ "CaseAddress5", _) => (k, faker.address().country())
    case (k @ "CasePostCode", _) => (k, faker.address().zipCode())
    case (k @ "CaseTelephoneNo", _) => (k, faker.phoneNumber().phoneNumber())
    case (k @ "CaseFaxNo", _) => (k, faker.phoneNumber().phoneNumber())
    case (k @ "CaseAgentName", _) => (k, faker.name().fullName())
    case (k @ "LiabilityPortOfficerName", _) => (k, faker.name().fullName())
    case (k @ "LiabilityPortOfficerTel", _) => (k, faker.phoneNumber().cellPhone())
    case (k @ "SuppressUserName", _) => (k, faker.name().fullName())
    case (k @ "SuppressReason", _) => (k, faker.lorem().sentence())
    case (k @ "CaseEmail", _) => (k, faker.internet().emailAddress())
    case (k @ "ContactName", _) => (k, faker.name().fullName())
    case other => other
  }

  private def anonymizeTblCaseBTI(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k @ "Keywords1", _) => (k, faker.lorem().word())
    case (k @ "Keywords2", _) => (k, faker.lorem().word())
    case (k @ "Keywords3", _) => (k, faker.lorem().word())
    case (k @ "Keywords4", _) => (k, faker.lorem().word())
    case (k @ "Keywords5", _) => (k, faker.lorem().word())
    case (k @ "Keywords6", _) => (k, faker.lorem().word())
    case (k @ "Keywords7", _) => (k, faker.lorem().word())
    case (k @ "Keywords8", _) => (k, faker.lorem().word())
    case (k @ "Keywords9", _) => (k, faker.lorem().word())
    case (k @ "Keywords10", _) => (k, faker.lorem().word())
    case (k @ "Keywords11", _) => (k, faker.lorem().word())
    case (k @ "Keywords12", _) => (k, faker.lorem().word())
    case (k @ "Keywords13", _) => (k, faker.lorem().word())
    case (k @ "Keywords14", _) => (k, faker.lorem().word())
    case (k @ "Keywords15", _) => (k, faker.lorem().word())
    case (k @ "Keywords16", _) => (k, faker.lorem().word())
    case (k @ "Keywords17", _) => (k, faker.lorem().word())
    case (k @ "Keywords18", _) => (k, faker.lorem().word())
    case (k @ "Keywords19", _) => (k, faker.lorem().word())
    case (k @ "Keywords20", _) => (k, faker.lorem().word())
    case other => other
  }

  private def anonymizeTblCaseClassMeth(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k @ "MethodSearch", _) => (k, faker.lorem().paragraph())
    case (k @ "Justification", _) => (k, faker.lorem().paragraph())
    case (k @ "GoodsDescription", _) => (k, faker.lorem().paragraph())
    case (k @ "LGCExpertAdvice", _) => (k, faker.lorem().paragraph())
    case other => other
  }

  private def anonymizeTblImages(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k @ "Description", _) => (k, faker.lorem().paragraph())
    case (k @ "FileName", _) => (k, faker.file().fileName())
    case other => other
  }

  private def anonymizeTblMovement(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k @ "Reason", _) => (k, faker.lorem().paragraph())
    case other => other
  }

  private def anonymizeTblSample(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k @ "Action", _) => (k, faker.lorem().word())
    case other => other
  }

  private def anonymizeTblUser(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k @ "FirstName", _) => (k, faker.name().firstName())
    case (k @ "LastName", _) => (k, faker.name().lastName())
    case (k @ "Extension", _) => (k, faker.phoneNumber().extension())
    case (k @ "Email", _) => (k, faker.internet().emailAddress())
    case other => other
  }

  private def anonymizeTblCaseLMComments(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k @ "Band7Name", _) => (k, faker.name().fullName())
    case (k @ "Band7Comments", _) => (k, faker.lorem().paragraph())
    case (k @ "Band9Name", _) => (k, faker.name().fullName())
    case (k @ "Band9Comments", _) => (k, faker.lorem().paragraph())
    case (k @ "Band11Name", _) => (k, faker.name().fullName())
    case (k @ "Band11Comments", _) => (k, faker.lorem().paragraph())
    case other => other
  }
}
