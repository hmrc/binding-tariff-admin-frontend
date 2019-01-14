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

package uk.gov.hmrc.bindingtariffadminfrontend.config

import java.util.concurrent.TimeUnit

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration.FiniteDuration

class AppConfigSpec extends UnitSpec with GuiceOneAppPerSuite {

  private def configWith(pairs: (String, String)*): AppConfig = {
    new AppConfig(Configuration.from(pairs.map(e => e._1 -> e._2).toMap), Environment.simple())
  }

  "Config" should {
    "throw error on missing config key" in {
      intercept[Exception]{
        configWith().analyticsToken
      }.getMessage shouldBe "Missing configuration key: google-analytics.token"
    }

    "Build Filestore URL" in {
      configWith(
        "microservice.services.binding-tariff-filestore.port" -> "8080",
        "microservice.services.binding-tariff-filestore.host" -> "localhost",
        "microservice.services.binding-tariff-filestore.protocol" -> "http"
      ).filestoreUrl shouldBe "http://localhost:8080"
    }

    "Build Classification Backend URL" in {
      configWith(
        "microservice.services.binding-tariff-classification.port" -> "8080",
        "microservice.services.binding-tariff-classification.host" -> "localhost",
        "microservice.services.binding-tariff-classification.protocol" -> "http"
      ).classificationBackendUrl shouldBe "http://localhost:8080"
    }

    "Build Data Migration Lock Lifetime" in {
      configWith("scheduler.data-migration.lock-lifetime" -> "60s").dataMigrationLockLifetime shouldBe FiniteDuration(60, TimeUnit.SECONDS)
    }

    "Build Data Migration Interval" in {
      configWith("scheduler.data-migration.interval" -> "60s").dataMigrationInterval shouldBe FiniteDuration(60, TimeUnit.SECONDS)
    }
  }

}
