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

import org.scalacheck.{Gen, Shrink}
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.bindingtariffadminfrontend.model.Store
import uk.gov.hmrc.bindingtariffadminfrontend.model.Store._

class ResetFormProviderSpec extends WordSpec with OptionValues with Matchers with ScalaCheckDrivenPropertyChecks {

  implicit val dontShrink: Shrink[String] = Shrink.shrinkAny

  private def stores: Gen[Set[Store]] = Gen.listOf(Gen.oneOf(Store.values)).map(_.toSet)

  private val form = new ResetFormProvider().apply

  ".store" must {
    val fieldName = "store"

    "bind selected stores" in {
      forAll(stores) { stores =>
        val input = stores.zipWithIndex.map {
          case (store, index) => s"$fieldName[$index]" -> store.toString
        }.toMap
        val result = form.bind(input)
        result.value.value shouldBe stores
      }
    }
  }
}
