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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.forms.{ResetFormProvider, ResetMigrationFormProvider}
import uk.gov.hmrc.bindingtariffadminfrontend.model.Store
import uk.gov.hmrc.bindingtariffadminfrontend.service.{DataMigrationService, ResetService}
import uk.gov.hmrc.bindingtariffadminfrontend.views.html.{reset_confirm, reset_migration_confirm, reset_migration_results}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

@Singleton
class ResetController @Inject() (
  authenticatedAction: AuthenticatedAction,
  resetService: ResetService,
  dataMigrationService: DataMigrationService,
  resetFormProvider: ResetFormProvider,
  resetMigrationFormProvider: ResetMigrationFormProvider,
  mcc: MessagesControllerComponents,
  override val messagesApi: MessagesApi,
  implicit val appConfig: AppConfig
) extends FrontendController(mcc)
    with I18nSupport {
  private val resetForm          = resetFormProvider.apply
  private val resetMigrationForm = resetMigrationFormProvider.apply

  def reset(): Action[AnyContent] = authenticatedAction.async { implicit request =>
    if (appConfig.resetPermitted) {
      successful(Ok(reset_confirm(resetForm.fill(Store.defaultValues))))
    } else {
      successful(Redirect(routes.IndexController.get()))
    }
  }

  def resetConfirm(): Action[AnyContent] = authenticatedAction.async { implicit request =>
    if (appConfig.resetPermitted) {
      resetForm.bindFromRequest.fold(
        errors => successful(Ok(reset_confirm(errors))),
        stores => resetService.resetEnvironment(stores).map(_ => Redirect(routes.DataMigrationStateController.get()))
      )
    } else {
      successful(Redirect(routes.DataMigrationStateController.get()))
    }
  }

  def resetMigration(): Action[AnyContent] = authenticatedAction.async { implicit request =>
    if (appConfig.resetMigrationPermitted) {
      for {
        migrationCounts   <- dataMigrationService.counts
        migratedCaseCount <- dataMigrationService.migratedCaseCount
      } yield Ok(reset_migration_confirm(resetMigrationForm, migrationCounts, migratedCaseCount))
    } else {
      successful(Redirect(routes.IndexController.get()))
    }
  }

  def resetMigrationConfirm(): Action[AnyContent] = authenticatedAction.async { implicit request =>
    if (appConfig.resetMigrationPermitted) {
      resetMigrationForm.bindFromRequest.fold(
        errors =>
          for {
            migrationCounts   <- dataMigrationService.counts
            migratedCaseCount <- dataMigrationService.migratedCaseCount
          } yield Ok(reset_migration_confirm(errors, migrationCounts, migratedCaseCount)), {
          case true =>
            for {
              migratedBefore <- dataMigrationService.migratedCaseCount
              _              <- resetService.resetMigratedCases
              migratedAfter  <- dataMigrationService.migratedCaseCount
              totalCaseCount <- dataMigrationService.totalCaseCount
            } yield Ok(reset_migration_results(migratedBefore, migratedAfter, totalCaseCount))
          case false => successful(Redirect(routes.IndexController.get()))
        }
      )
    } else {
      successful(Redirect(routes.IndexController.get()))
    }
  }
}
