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

package uk.gov.hmrc.bindingtariffadminfrontend.model

import play.api.libs.json.Format
import play.api.mvc.PathBindable
import uk.gov.hmrc.bindingtariffadminfrontend.util.JsonUtil

import scala.util.Try

object ScheduledJob extends Enumeration {
  type ScheduledJob = Value
  val DAYS_ELAPSED = Value

  def apply(string: String): Option[ScheduledJob] = {
    values.find(_.toString == string)
  }

  implicit val format: Format[ScheduledJob.Value] = JsonUtil.format(ScheduledJob)

  implicit val binder: PathBindable[ScheduledJob] = new PathBindable[ScheduledJob] {
    override def bind(key: String, value: String): Either[String, ScheduledJob] =
      Try(ScheduledJob.withName(value)).map(Right(_)).getOrElse(Left(value))

    override def unbind(key: String, value: ScheduledJob): String = value.toString
  }
}
