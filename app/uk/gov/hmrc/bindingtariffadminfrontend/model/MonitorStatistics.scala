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

import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.ApplicationType
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.ApplicationType.ApplicationType

case class MonitorStatistics(
  submittedCases: Map[ApplicationType, Int],
  migratedCases: Map[ApplicationType, Int],
  publishedFileCount: Int,
  unpublishedFileCount: Int,
  migratedAttachmentCount: Int
) {
  val allCases: Map[ApplicationType, Int] = ApplicationType.values
    .map(applicationType =>
      applicationType -> (submittedCases.getOrElse(applicationType, 0) + migratedCases.getOrElse(applicationType, 0))
    )
    .toMap

  val totalCaseCount: Int = allCases.values.sum

  val totalFileCount: Int = publishedFileCount + unpublishedFileCount
}
