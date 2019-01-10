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

package uk.gov.hmrc.bindingtariffadminfrontend.service

import javax.inject.Inject
import uk.gov.hmrc.bindingtariffadminfrontend.model.Case

import scala.concurrent.Future

class DataMigrationService @Inject()(/*repository: CaseMigrationRepository*/){

  def getState: Future[Seq[Case]] = {
    Future.successful(Seq.empty)
  }

  def isProcessing: Future[Boolean] = {
    Future.successful(false)
  }

  def prepareMigration(cases: Seq[Case]): Future[Boolean] = {
    Future.successful(true)
  }

  def process(c: Case): Future[Case] = {

    Future.successful(c)
  }

}
