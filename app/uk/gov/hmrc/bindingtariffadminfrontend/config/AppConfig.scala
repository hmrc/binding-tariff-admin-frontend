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

package uk.gov.hmrc.bindingtariffadminfrontend.config

import java.time.Clock

import javax.inject.{Inject, Singleton}
import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import uk.gov.hmrc.bindingtariffadminfrontend.model.Credentials
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.duration.FiniteDuration

@Singleton
class AppConfig @Inject()(val runModeConfiguration: Configuration, environment: Environment) extends ServicesConfig {

  override protected def mode: Mode = environment.mode

  private def loadConfig(key: String) = runModeConfiguration.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  lazy val analyticsToken: String = loadConfig("google-analytics.token")
  lazy val analyticsHost: String = loadConfig("google-analytics.host")

  lazy val filestoreUrl: String = baseUrl("binding-tariff-filestore")
  lazy val dataMigrationUrl: String = baseUrl("binding-tariff-data-transformation")
  lazy val classificationBackendUrl: String = baseUrl("binding-tariff-classification")
  lazy val rulingUrl: String = baseUrl("binding-tariff-ruling-frontend")
  lazy val internalServiceUrl: String = loadConfig("tariff-classification-frontend")
  lazy val dataMigrationLockLifetime: FiniteDuration = getDuration("scheduler.data-migration.lock-lifetime").asInstanceOf[FiniteDuration]
  lazy val dataMigrationInterval: FiniteDuration = getDuration("scheduler.data-migration.interval").asInstanceOf[FiniteDuration]
  lazy val resetPermitted: Boolean = getBoolean("reset-permitted")
  lazy val pageSize: Int = getInt("page-size")
  lazy val apiToken: String = loadConfig("auth.api-token")
  lazy val clock: Clock = Clock.systemUTC()
  lazy val credentials: Seq[Credentials] = getString("auth.credentials")
    .split(",")
    .map(_.split(":") match {
      case Array(username, hash) => Credentials(username.trim, hash.trim)
    })

}
