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
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText, set}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.Store
import uk.gov.hmrc.bindingtariffadminfrontend.model.Store.Store
import uk.gov.hmrc.bindingtariffadminfrontend.service.ResetService
import uk.gov.hmrc.bindingtariffadminfrontend.views.html.reset_confirm
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

@Singleton
class ResetController @Inject() (
  authenticatedAction: AuthenticatedAction,
  service: ResetService,
  mcc: MessagesControllerComponents,
  override val messagesApi: MessagesApi,
  implicit val appConfig: AppConfig
) extends FrontendController(mcc)
    with I18nSupport {
  private val form: Form[Set[Store]] = Form(
    mapping[Set[Store], Set[Store]](
      "store" -> set(
        nonEmptyText.verifying(v => Store.values.exists(v == _.toString)).transform(Store(_).get, _.toString)
      )
    )(identity)(Some(_))
  ).fill(Store.defaultValues)

  def reset(): Action[AnyContent] = authenticatedAction.async { implicit request =>
    if (appConfig.resetPermitted) {
      successful(Ok(reset_confirm(form)))
    } else {
      successful(Redirect(routes.IndexController.get()))
    }
  }

  def resetConfirm(): Action[AnyContent] = authenticatedAction.async { implicit request =>
    if (appConfig.resetPermitted) {
      form.bindFromRequest.fold(
        errors => successful(Ok(reset_confirm(errors))),
        stores => service.resetEnvironment(stores).map(_ => Redirect(routes.DataMigrationStateController.get()))
      )
    } else {
      successful(Redirect(routes.DataMigrationStateController.get()))
    }
  }
}
