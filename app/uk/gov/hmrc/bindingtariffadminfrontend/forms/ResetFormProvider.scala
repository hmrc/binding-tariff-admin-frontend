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

package uk.gov.hmrc.bindingtariffadminfrontend.forms

import javax.inject.Inject
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText, set}
import uk.gov.hmrc.bindingtariffadminfrontend.model.Store
import uk.gov.hmrc.bindingtariffadminfrontend.model.Store.Store

class ResetFormProvider @Inject() () {
  def apply: Form[Set[Store]] =
    Form(
      mapping[Set[Store], Set[Store]](
        "store" -> set(
          nonEmptyText.verifying(v => Store.values.exists(v == _.toString)).transform(Store(_).get, _.toString)
        )
      )(identity)(Some(_))
    )
}
