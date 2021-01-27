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

package uk.gov.hmrc.bindingtariffadminfrontend.model

import play.api.libs.json.Format
import play.api.mvc.PathBindable
import uk.gov.hmrc.bindingtariffadminfrontend.util.JsonUtil

import scala.util.Try

object CaseUpdateTarget extends Enumeration {
  type CaseUpdateTarget = Value
  val LIABILITIES_APPLICATION_TRADERNAME = Value

  implicit val format: Format[CaseUpdateTarget] = JsonUtil.format(CaseUpdateTarget)

  implicit val binder: PathBindable[CaseUpdateTarget] = new PathBindable[CaseUpdateTarget] {
    override def bind(key: String, value: String): Either[String, CaseUpdateTarget] =
      Try(CaseUpdateTarget.withName(value)).map(Right(_)).getOrElse(Left(value))

    override def unbind(key: String, value: CaseUpdateTarget): String = value.toString
  }
}
