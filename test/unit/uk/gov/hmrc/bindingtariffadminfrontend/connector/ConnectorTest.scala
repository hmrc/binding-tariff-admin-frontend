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

package uk.gov.hmrc.bindingtariffadminfrontend.connector

import akka.actor.ActorSystem
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import play.api.libs.ws.WSClient
import uk.gov.hmrc.bindingtariffadminfrontend.base.BaseSpec
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

trait ConnectorTest extends BaseSpec with WiremockTestServer with BeforeAndAfterEach {

  private val actorSystem = ActorSystem.create("testActorSystem")

  protected val wsClient: WSClient = app.injector.instanceOf[WSClient]

  protected val httpAuditing: HttpAuditing = app.injector.instanceOf[HttpAuditing]
  protected val authenticatedHttpClient    = new AuthenticatedHttpClient(httpAuditing, wsClient, actorSystem, realConfig)
  protected val standardHttpClient         = new DefaultHttpClient(app.configuration, httpAuditing, wsClient, actorSystem)

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockAppConfig.rulingUrl) thenReturn getUrl
    when(mockAppConfig.filestoreUrl) thenReturn getUrl
    when(mockAppConfig.classificationBackendUrl) thenReturn getUrl
    when(mockAppConfig.dataMigrationUrl) thenReturn getUrl
  }

}
