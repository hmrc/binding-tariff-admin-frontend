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

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigrationJob.MigrationJob
import uk.gov.hmrc.bindingtariffadminfrontend.service.AdminMonitorService
import uk.gov.hmrc.bindingtariffadminfrontend.views
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

@Singleton
class MigrationJobController @Inject() (
  authenticatedAction: AuthenticatedAction,
  adminMonitorService: AdminMonitorService,
  mcc: MessagesControllerComponents,
  override val messagesApi: MessagesApi,
  implicit val appConfig: AppConfig
) extends FrontendController(mcc)
    with I18nSupport {

  def get: Action[AnyContent] =
    authenticatedAction.async(implicit request => successful(Ok(views.html.migration_jobs())))

  def post(job: MigrationJob): Action[AnyContent] = authenticatedAction.async { implicit request =>
    adminMonitorService
      .runMigrationJob(job)
      .map(_ => Ok(views.html.migration_jobs(Some(job))))
  }

}
