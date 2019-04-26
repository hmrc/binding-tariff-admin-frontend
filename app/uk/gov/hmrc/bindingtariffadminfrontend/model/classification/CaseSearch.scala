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

package uk.gov.hmrc.bindingtariffadminfrontend.model.classification

import java.time.Instant

import play.api.data.Forms._
import play.api.data.{Form, Mapping}
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.ApplicationType.ApplicationType
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.SortDirection.SortDirection
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.SortField.SortField

import scala.util.Try

case class CaseSearch
(
  reference: Option[Set[String]] = None,
  applicationType: Option[ApplicationType] = None,
  queueId: Option[String] = None,
  eori: Option[String] = None,
  assigneeId: Option[String] = None,
  statuses: Option[Set[CaseStatus]] = None,
  traderName: Option[String] = None,
  minDecisionEnd: Option[Instant] = None,
  commodityCode: Option[String] = None,
  decisionDetails: Option[String] = None,
  keywords: Option[Set[String]] = None,
  sortField: Option[SortField] = None,
  sortDirection: Option[SortDirection] = None
)

object CaseSearch {

  val referenceKey = "reference"
  val applicationTypeKey = "application_type"
  val queueIdKey = "queue_id"
  val eoriKey = "eori"
  val assigneeIdKey = "assignee_id"
  val statusKey = "status"
  val traderNameKey = "trader_name"
  val minDecisionEndKey = "min_decision_end"
  val commodityCodeKey = "commodity_code"
  val decisionDetailsKey = "decision_details"
  val sortFieldKey = "sort_by"
  val sortDirectionKey = "sort_direction"
  val keywordKey = "keyword"

  implicit def bindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[CaseSearch] = new QueryStringBindable[CaseSearch] {

    private def bindCaseStatus(key: String): Option[CaseStatus] = {
      CaseStatus.values.find(_.toString.equalsIgnoreCase(key))
    }

    private def bindApplicationType(key: String): Option[ApplicationType] = {
      ApplicationType.values.find(_.toString.equalsIgnoreCase(key))
    }

    private def bindSortField(key: String): Option[SortField] = {
      SortField.values.find(_.toString.equalsIgnoreCase(key))
    }

    private def bindSortDirection(key: String): Option[SortDirection] = {
      SortDirection.values.find(_.toString.equalsIgnoreCase(key))
    }

    private def bindInstant(key: String): Option[Instant] = Try(Instant.parse(key)).toOption

    override def bind(key: String, requestParams: Map[String, Seq[String]]): Option[Either[String, CaseSearch]] = {

      def params(name: String): Option[Set[String]] = {
        requestParams.get(name).map(_.flatMap(_.split(",")).toSet).filter(_.exists(_.nonEmpty))
      }

      def param(name: String): Option[String] = {
        params(name).map(_.head)
      }

      Some(
        Right(
          CaseSearch(
            reference = params(referenceKey),
            applicationType = param(applicationTypeKey).flatMap(bindApplicationType),
            queueId = param(queueIdKey),
            eori = param(eoriKey),
            assigneeId = param(assigneeIdKey),
            statuses = params(statusKey).map(_.map(bindCaseStatus).filter(_.isDefined).map(_.get)),
            traderName = param(traderNameKey),
            minDecisionEnd = param(minDecisionEndKey).flatMap(bindInstant),
            commodityCode = param(commodityCodeKey),
            decisionDetails = param(decisionDetailsKey),
            keywords = params(keywordKey).map(_.map(_.toUpperCase)),
            sortField = param(sortFieldKey).flatMap(bindSortField),
            sortDirection = param(sortDirectionKey).flatMap(bindSortDirection)
          )
        )
      )
    }

    override def unbind(key: String, filter: CaseSearch): String = {
      Seq(
        filter.reference.map(_.map(r => stringBinder.unbind(referenceKey, r)).mkString("&")),
        filter.applicationType.map(t => stringBinder.unbind(applicationTypeKey, t.toString)),
        filter.queueId.map(stringBinder.unbind(queueIdKey, _)),
        filter.eori.map(stringBinder.unbind(eoriKey, _)),
        filter.assigneeId.map(stringBinder.unbind(assigneeIdKey, _)),
        filter.statuses.map(_.map(s => stringBinder.unbind(statusKey, s.toString)).mkString("&")),
        filter.traderName.map(stringBinder.unbind(traderNameKey, _)),
        filter.minDecisionEnd.map(i => stringBinder.unbind(minDecisionEndKey, i.toString)),
        filter.commodityCode.map(stringBinder.unbind(commodityCodeKey, _)),
        filter.decisionDetails.map(stringBinder.unbind(decisionDetailsKey, _)),
        filter.keywords.map(_.map(s => stringBinder.unbind(keywordKey, s.toString)).mkString("&")),
        filter.sortField.map(f => stringBinder.unbind(sortFieldKey, f.toString)),
        filter.sortDirection.map(d => stringBinder.unbind(sortDirectionKey, d.toString))
      ).filter(_.isDefined).map(_.get).mkString("&")
    }
  }

  private def textTransformingToSet[A](reader: String => A, writer: A => String): Mapping[Set[A]] = {
    def splitAndMap(s: String): Set[A] = s.split(",").map(_.trim).map(reader).toSet
    textTransformingTo[Set[A]](splitAndMap, _.mkString(","))
  }

  private def textTransformingTo[A](reader: String => A, writer: A => String): Mapping[A] = {
    nonEmptyText
      .verifying("Invalid entry", s => Try(reader(s)).isSuccess)
      .transform[A](reader, writer)
  }

  val form: Form[CaseSearch] = Form(
    mapping(
      referenceKey -> optional[Set[String]](textTransformingToSet(identity, identity)),
      applicationTypeKey -> optional[ApplicationType](textTransformingTo(ApplicationType.withName, _.toString)),
      queueIdKey -> optional[String](text),
      eoriKey -> optional[String](text),
      assigneeIdKey -> optional[String](text),
      statusKey -> optional[Set[CaseStatus]](textTransformingToSet(CaseStatus.withName, _.toString)),
      traderNameKey -> optional[String](text),
      minDecisionEndKey -> optional[Instant](textTransformingTo(Instant.parse, _.toString)),
      commodityCodeKey -> optional[String](text),
      decisionDetailsKey -> optional[String](text),
      keywordKey -> optional[Set[String]](textTransformingToSet(identity, identity)),
      sortFieldKey -> optional[SortField](textTransformingTo(SortField.withName, _.toString)),
      sortDirectionKey -> optional[SortDirection](textTransformingTo(SortDirection.withName, _.toString))
    )(CaseSearch.apply)(CaseSearch.unapply)
  )
}