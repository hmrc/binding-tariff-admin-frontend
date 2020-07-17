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

import javax.inject.{Inject, Singleton}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.service.AdminMonitorService
import uk.gov.hmrc.bindingtariffadminfrontend.views.html.monitor
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class MonitorController @Inject()(
                                   authenticatedAction: AuthenticatedAction,
                                monitorService: AdminMonitorService,
                                   mcc: MessagesControllerComponents,
                                   override val messagesApi: MessagesApi,
                                implicit val appConfig: AppConfig
                                 ) extends FrontendController(mcc) with I18nSupport {

  def get: Action[AnyContent] = authenticatedAction.async { implicit request =>
    for {
      cases <- monitorService.countCases
      publishedFiles <-  monitorService.countPublishedFiles
      unpublishedFiles <-  monitorService.countUnpublishedFiles
    } yield Ok(monitor(cases, publishedFiles, unpublishedFiles))
  }

}
