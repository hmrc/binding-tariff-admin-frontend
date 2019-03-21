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

package uk.gov.hmrc.bindingtariffadminfrontend.service

import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffadminfrontend.connector.{BindingTariffClassificationConnector, FileStoreConnector}
import uk.gov.hmrc.bindingtariffadminfrontend.model.{Paged, Pagination}
import uk.gov.hmrc.bindingtariffadminfrontend.model.classification.Case
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.{FileUploaded, Search}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AdminMonitorServiceTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val btcConnector = mock[BindingTariffClassificationConnector]
  private val fileConnector = mock[FileStoreConnector]
  private val service = new AdminMonitorService(btcConnector, fileConnector)


  "Count Cases" should {
    "Delegate to connector" in {
      given(btcConnector.getCases()(any[HeaderCarrier])) willReturn Future.successful(Paged(Seq.empty[Case], 0, 0, 1))
      await(service.countCases) shouldBe 1
    }
  }

  "Count Published Files" should {
    "Delegate to connector" in {
      given(fileConnector.find(refEq(Search(published = Some(true))), refEq(Pagination(1, 1)))(any[HeaderCarrier])) willReturn Future.successful(Paged.empty[FileUploaded])
      await(service.countPublishedFiles) shouldBe 0
    }
  }

  "Count Unpublished Files" should {
    "Delegate to connector" in {
      given(fileConnector.find(refEq(Search(published = Some(false))), refEq(Pagination(1, 1)))(any[HeaderCarrier])) willReturn Future.successful(Paged.empty[FileUploaded])
      await(service.countUnpublishedFiles) shouldBe 0
    }
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(btcConnector, fileConnector)
  }
}
