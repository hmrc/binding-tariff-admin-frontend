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

object MigrationJob extends Enumeration {
  type MigrationJob = Value

  val AMEND_DATE_OF_EXTRACT = Value

  def apply(string: String): Option[MigrationJob] =
    values.find(_.toString == string)

  implicit val format: Format[MigrationJob.Value] = JsonUtil.format(MigrationJob)

  implicit val binder: PathBindable[MigrationJob] = new PathBindable[MigrationJob] {
    override def bind(key: String, value: String): Either[String, MigrationJob] =
      Try(MigrationJob.withName(value)).map(Right(_)).getOrElse(Left(value))

    override def unbind(key: String, value: MigrationJob): String = value.toString
  }
}
