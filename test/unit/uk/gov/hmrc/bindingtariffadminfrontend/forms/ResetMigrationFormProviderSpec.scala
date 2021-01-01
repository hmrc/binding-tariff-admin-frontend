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

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Gen, Shrink}
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.data.FormError

class ResetMigrationFormProviderSpec
    extends WordSpec
    with OptionValues
    with Matchers
    with ScalaCheckDrivenPropertyChecks {

  implicit val dontShrink: Shrink[String] = Shrink.shrinkAny

  private def nonBooleans: Gen[String] =
    arbitrary[String]
      .suchThat(_.nonEmpty)
      .suchThat(_ != "true")
      .suchThat(_ != "false")

  private val form = new ResetMigrationFormProvider().apply

  ".confirm" must {
    val fieldName    = "confirm"
    val invalidError = FormError(fieldName, "error.boolean")

    "bind true" in {
      val result = form.bind(Map(fieldName -> "true"))
      result.value.value shouldBe true
    }

    "bind false" in {
      val result = form.bind(Map(fieldName -> "false"))
      result.value.value shouldBe false
    }

    "not bind non-booleans" in {
      forAll(nonBooleans -> "nonBoolean") { nonBoolean =>
        val result = form.bind(Map(fieldName -> nonBoolean)).apply(fieldName)
        result.errors shouldEqual Seq(invalidError)
      }
    }
  }
}
