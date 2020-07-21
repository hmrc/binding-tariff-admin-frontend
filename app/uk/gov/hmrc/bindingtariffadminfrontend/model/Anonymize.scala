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

import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

import com.github.javafaker._
import java.{util => ju}
import java.util.UUID.randomUUID

import scala.util.Random

object Anonymize {
  private val faker = new Faker(ju.Locale.UK)

  val anonymized: String = "???"
  def anonymizing: Any => String = _ => anonymized
  val dateFormat = new SimpleDateFormat("yyyyMMdd")
  val timeFormat = new SimpleDateFormat("HHmmss")

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
    case _ if tblName.contains("tblCaseLMComments") =>
      anonymizeTblCaseLMComments(data)
    case _ =>
      throw new AnonymizationFailedException(s"The file name ${tblName} was not recognised")
  }

  private def anonymizeEBTIApplication(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k, "NULL") => (k, "NULL")
    case (k @ "PreviousBTIRef", _) => (k, faker.number().digits(35))
    case (k @ "PreviousBTICountry", _) => (k, faker.country().name())
    case (k @ "PreviousDateofValidity", _) => (k, dateFormat.format(faker.date().past(50, TimeUnit.DAYS)))
    case (k @ "PreviousNomenclatureCode", _) => (k, faker.number().digits(10))
    case (k @ "EnvisagedNomenclature", _) => (k, faker.number().digits(10))
    case (k @ "GoodsDescription", _) => (k, faker.lorem().paragraph())
    case (k @ "AdditionalInfo", _) => (k, faker.lorem().paragraph())
    case (k @ "AdditionalInfo2", _) => (k, faker.lorem().paragraph())
    case (k @ "Country1", _) => (k, faker.country().countryCode2())
    case (k @ "Place1", _) => (k, faker.country().capital())
    case (k @ "DateOfApplication1", _) => (k, dateFormat.format(faker.date().past(50, TimeUnit.DAYS)))
    case (k @ "OtherBTIRef1", _) => (k, faker.number().digits(29))
    case (k @ "DateOfValidity1", _) => (k, dateFormat.format(faker.date().past(50, TimeUnit.DAYS)))
    case (k @ "OtherNomenclatureCode1", _) => (k, faker.number().digits(10))
    case (k @ "Country2", _) => (k, faker.country().countryCode2())
    case (k @ "Place2", _) => (k, faker.country().capital())
    case (k @ "DateOfApplication2", _) => (k, dateFormat.format(faker.date().past(50, TimeUnit.DAYS)))
    case (k @ "OtherBTIRef2", _) => (k, faker.lorem().characters(29))
    case (k @ "DateOfValidity2", _) => (k, dateFormat.format(faker.date().past(50, TimeUnit.DAYS)))
    case (k @ "OtherNomenclatureCode2", _) => (k, faker.number().digits(10))
    case (k @ "HolderCountry1", _) => (k, faker.country().countryCode2())
    case (k @ "HolderOtherBTIRef1", _) => (k, faker.number().digits(29))
    case (k @ "HolderDateOfValidity1", _) => (k, dateFormat.format(faker.date().past(50, TimeUnit.DAYS)))
    case (k @ "HolderOtherNomenclatureCode1", _) => (k, faker.number().digits(10))
    case (k @ "HolderCountry2", _) => (k, faker.country().countryCode2())
    case (k @ "HolderOtherBTIRef2", _) => (k, faker.lorem().characters(29))
    case (k @ "HolderDateOfValidity2", _) => (k, dateFormat.format(faker.date().past(50, TimeUnit.DAYS)))
    case (k @ "HolderOtherNomenclatureCode2", _) => (k, faker.number().digits(10))
    case (k @ "Contact", _) => (k, faker.name().fullName())
    case (k @ "VATRegTurnNo", _) => (k, faker.number().digits(12))
    case (k @ "CustomsOther", _) => (k, faker.lorem().characters(70))
    case (k @ "EnvisagedSpecialDesc", _) => (k, faker.lorem().characters(70))
    case (k @ "Signature", _) => (k, faker.lorem().characters(256))
    case (k @ "SignatureDate", _) => (k, dateFormat.format(faker.date().past(50, TimeUnit.DAYS)))
    case (k @ "CombinedNomenclature", _) => (k, faker.number().digits(8))
    case (k @ "TARICCode", _) => (k, faker.number().digits(2))
    case (k @ "TARICAdditionalCode1", _) => (k, faker.number().digits(4))
    case (k @ "TARICAdditionalCode2", _) => (k, faker.number().digits(4))
    case (k @ "NationalAdditionalCode", _) => (k, faker.number().digits(4))
    case (k @ "OtherNomenclature", _) => (k, faker.number().digits(22))
    case other => other
  }

  private def anonymizeEBTIAddresses(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k, "NULL") => (k, "NULL")
    case (k @ "Name", _) => (k, faker.name.fullName())
    case (k @ "Address1", _) => (k, faker.address().buildingNumber())
    case (k @ "Address2", _) => (k, faker.address().streetAddress())
    case (k @ "Address3", _) => (k, faker.address().state())
    case (k @ "Postcode", _) => (k, faker.address().zipCode())
    case (k @ "Country", _) => (k, faker.country().countryCode2())
    case (k @ "TelephoneNo", _) => (k, faker.phoneNumber().phoneNumber())
    case (k @ "FaxNo", _) => (k, faker.phoneNumber().phoneNumber())
    case (k @ "CustomsID", _) => (k, faker.lorem().characters(25))
    case (k @ "Type", _) => (k, faker.lorem().characters(25))
    case (k @ "Reference", _) => (k, faker.number().digits(6))
    case (k @ "City", _) => (k, faker.address().city())
    case (k @ "Email", _) => (k, faker.internet().emailAddress())
    case other => other
  }

  private def anonymizeTblCaseRecord(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k, "NULL") => (k, "NULL")
    case (k @ "InsCountryCode", _) => (k, faker.country().countryCode2())
    case (k @ "InsGoodsID", _) => (k, faker.lorem().characters(50))
    case (k @ "InsBoardFileUserName", _) => (k, faker.lorem().characters(50))
    case (k @ "InsBoardFileDatePrinted", _) => (k, dateFormat.format(faker.date().past(10, TimeUnit.DAYS)))
    case (k @ "CaseName", _) => (k, faker.lorem().characters(70))
    case (k @ "CaseBoardsFileNumber", _) => (k, faker.number().digits(50))
    case (k @ "CaseClosedReason", _) => (k, faker.lorem().characters(3))
    case (k @ "CaseInterimReplySentDate", _) => (k, dateFormat.format(faker.date().past(10, TimeUnit.DAYS)))
    case (k @ "CaseBoardFileRequestDate", _) => (k, dateFormat.format(faker.date().past(10, TimeUnit.DAYS)))
    case (k @ "CaseAddress1", _) => (k, faker.address().buildingNumber())
    case (k @ "CaseAddress2", _) => (k, faker.address().streetAddress())
    case (k @ "CaseAddress3", _) => (k, faker.address().city())
    case (k @ "CaseAddress4", _) => (k, faker.address().state())
    case (k @ "CaseAddress5", _) => (k, faker.address().country())
    case (k @ "CasePostCode", _) => (k, faker.address().zipCode())
    case (k @ "CaseTelephoneNo", _) => (k, faker.phoneNumber().phoneNumber())
    case (k @ "CaseFaxNo", _) => (k, faker.phoneNumber().phoneNumber())
    case (k @ "CaseAgentName", _) => (k, faker.name().fullName())
    case (k @ "CaseNameCompleted", _) => (k, faker.name().fullName())
    case (k @ "CaseSearchText", _) => (k, faker.animal().name())
    case (k @ "CaseReplacedBy", _) => (k, faker.aviation().aircraft())
    case (k @ "CaseReplacing", _) => (k, faker.beer().yeast())
    case (k @ "LiabilityPort", _) => (k, faker.book().genre())
    case (k @ "LiabilityPortOfficerName", _) => (k, faker.name().fullName())
    case (k @ "LiabilityPortOfficerLoc", _) => (k, faker.address().city())
    case (k @ "LiabilityPortOfficerTel", _) => (k, faker.phoneNumber().cellPhone())
    case (k @ "LiabilityEntryNo", _) => (k, faker.number().digits(50))
    case (k @ "AppealResult", _) => (k, faker.chuckNorris().fact())
    case (k @ "SupressDate", _) => (k, dateFormat.format(faker.date().past(10, TimeUnit.DAYS)))
    case (k @ "SupressTeam", _) => (k, faker.team().sport())
    case (k @ "SupressUserName", _) => (k, faker.name().username())
    case (k @ "SupressReason", _) => (k, faker.lordOfTheRings().location())
    case (k @ "ElapsedDaysInterim", _) => (k, faker.number().randomDigit().toString)
    case (k @ "ApplicationRef", _) => (k, faker.number().digits(10))
    case (k @ "AppealDownloaded", _) => (k, faker.number().numberBetween(0, 1).toString)
    case (k @ "CancelDownloaded", _) => (k, faker.number().numberBetween(0, 1).toString)
    case (k @ "BTILetterPrintDate", _) => (k, dateFormat.format(faker.date().past(10, TimeUnit.DAYS)))
    case (k @ "CustAuthKey", _) => (k, faker.number().numberBetween(0, 1).toString)
    case (k @ "CaseCustomsID", _) => (k, faker.number().digits(10))
    case (k @ "CaseEmail", _) => (k, faker.internet().emailAddress())
    case (k @ "ContactName", _) => (k, faker.name().fullName())
    case other => other
  }

  private def anonymizeTblCaseBTI(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k, "NULL") => (k, "NULL")
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
    case (k @ "ApplicationDate", _) => (k, dateFormat.format(faker.date().past(10, TimeUnit.DAYS)))
    case (k @ "PrintedDate", _) => (k, dateFormat.format(faker.date().past(10, TimeUnit.DAYS)))
    case (k @ "StatusDate", _) => (k, dateFormat.format(faker.date().past(10, TimeUnit.DAYS)))
    case (k @ "MISStatus", _) => (k, faker.lorem().word())
    case other => other
  }

  private def anonymizeTblCaseClassMeth(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k, "NULL") => (k, "NULL")
    case (k @ "MethodSearch", _) => (k, faker.lorem().paragraph())
    case (k @ "Justification", _) => (k, faker.lorem().paragraph())
    case (k @ "GoodsDescription", _) => (k, faker.lorem().paragraph())
    case (k @ "TARICCode", _) => (k, faker.number().digits(2))
    case (k @ "TARICAdditionalCode1", _) => (k, faker.number().digits(4))
    case (k @ "TARICAdditionalCode2", _) => (k, faker.number().digits(4))
    case (k @ "NationalAdditionalCode", _) => (k, faker.number().digits(4))
    case (k @ "CombinedNomenclature", _) => (k, faker.lorem().characters(8))
    case (k @ "ApplicantsCommodityCode", _) => (k, faker.number().digits(22))
    case (k @ "OTCCommodityCode", _) => (k, faker.number().digits(22))
    case (k @ "Exclusions", _) => (k, faker.lorem().paragraph())
    case (k @ "CommercialDenomenation", _) => (k, faker.beer().name())
    case (k @ "EBTISearch", _) => (k, faker.lorem().paragraph())
    case (k @ "BERTISearch", _) => (k, faker.lorem().paragraph())
    case other => other
  }

  private def anonymizeTblImages(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k, "NULL") => (k, "NULL")
    case (k @ "Description", _) => (k, faker.lorem().paragraph())
    case (k @ "DeletingUserID", _) => (k, faker.number().digits(8))
    case (k @ "DeletedTime", _) => (k, timeFormat.format(faker.date().past(10, TimeUnit.DAYS)))
    case (k @ "DeletedDate", _) => (k, dateFormat.format(faker.date().past(10, TimeUnit.DAYS)))
    case other => other
  }

  private def anonymizeTblMovement(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k, "NULL") => (k, "NULL")
    case (k @ "RecipientType", _) => (k, faker.lorem().characters(50))
    case (k @ "Reason", _) => (k, faker.lorem().paragraph())
    case other => other
  }

  private def anonymizeTblCaseLMComments(data: Map[String, String]): Map[String, String] = data.map {
    case (k, "") => (k, "")
    case (k, "NULL") => (k, "NULL")
    case (k @ "Band7Name", _) => (k, faker.name().fullName())
    case (k @ "Band7Comments", _) => (k, faker.lorem().paragraph())
    case (k @ "Band9Name", _) => (k, faker.name().fullName())
    case (k @ "Band9Comments", _) => (k, faker.lorem().paragraph())
    case (k @ "Band11Name", _) => (k, faker.name().fullName())
    case (k @ "Band11Comments", _) => (k, faker.lorem().paragraph())
    case other => other
  }
}
