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

import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec

class MigrationStateTest extends UnitSpec {

  "Migration State" should {
    val success = MigrationSuccess(())
    val failure = MigrationFailure((), new RuntimeException("error"))

    "Calculate isSuccess" in {
      success.isSuccess shouldBe true
      failure.isSuccess shouldBe false
    }

    "Calculate isFailure" in {
      success.isFailure shouldBe false
      failure.isFailure shouldBe true
    }

    "Convert to success" in {
      success.asSuccess shouldBe success
    }

    "Convert to failure" in {
      failure.asFailure shouldBe failure
    }

  }

}
