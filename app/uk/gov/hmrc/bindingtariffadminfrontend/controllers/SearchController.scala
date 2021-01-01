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

import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.{Paged, Pagination}
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification._
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileSearch, FileUploaded}
import uk.gov.hmrc.bindingtariffadminfrontend.service.AdminMonitorService
import uk.gov.hmrc.bindingtariffadminfrontend.views.html.search
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class SearchController @Inject() (
  authenticatedAction: AuthenticatedAction,
  monitorService: AdminMonitorService,
  mcc: MessagesControllerComponents,
  override val messagesApi: MessagesApi,
  implicit val appConfig: AppConfig
) extends FrontendController(mcc)
    with I18nSupport {

  private val form: Form[CaseSearch] = CaseSearch.form

  def get(s: CaseSearch, pagination: Pagination): Action[AnyContent] = authenticatedAction.async { implicit request =>
    form.bindFromRequest.fold(
      errors => Future.successful(Ok(search(errors, pagination))),
      query =>
        for {
          cases: Paged[Case] <- monitorService.getCases(query, pagination)

          attachmentIds: Set[String] = cases.results
            .flatMap(_.attachments)
            .map(_.id)
            .toSet

          agentLetterIds: Set[String] = cases.results
            .map(_.application)
            .filter(_.`type` == ApplicationType.BTI)
            .flatMap(_.asInstanceOf[BTIApplication].agent)
            .flatMap(_.letterOfAuthorisation)
            .map(_.id)
            .toSet

          fileSearch = FileSearch(ids = Some(attachmentIds ++ agentLetterIds))
          files: Paged[FileUploaded] <- if (cases.nonEmpty) monitorService.getFiles(fileSearch, Pagination.max)
                                       else Future.successful(Paged.empty[FileUploaded])

          eventSearch = EventSearch(Some(cases.results.map(_.reference).toSet))
          events: Paged[Event] <- if (cases.nonEmpty) monitorService.getEvents(eventSearch, Pagination.max)
                                 else Future.successful(Paged.empty[Event])
        } yield Ok(search(form.fill(query), pagination, cases.map(_.anonymize), files.results, events.results))
    )
  }

}
