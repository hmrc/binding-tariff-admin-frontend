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

package uk.gov.hmrc.bindingtariffadminfrontend.model

trait MigrationState[T] {
  val subject: T

  def isSuccess: Boolean = this.isInstanceOf[MigrationSuccess[T]]
  def isFailure: Boolean = this.isInstanceOf[MigrationFailure[T]]
  def asSuccess: MigrationSuccess[T] = this.asInstanceOf[MigrationSuccess[T]]
  def asFailure: MigrationFailure[T] = this.asInstanceOf[MigrationFailure[T]]
}

case class MigrationSuccess[T](override val subject: T) extends MigrationState[T]
case class MigrationFailure[T](override val subject: T, cause: Throwable) extends MigrationState[T]
