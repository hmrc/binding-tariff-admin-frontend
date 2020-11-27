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

package uk.gov.hmrc.bindingtariffadminfrontend.service

import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.connector._
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigrationStatus.MigrationStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model._
import uk.gov.hmrc.bindingtariffadminfrontend.repository.UploadRepository
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}

import scala.concurrent.Future

class ResetServiceTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val uploadRepository       = mock[UploadRepository]
  private val caseConnector          = mock[BindingTariffClassificationConnector]
  private val fileConnector          = mock[FileStoreConnector]
  private val rulingConnector        = mock[RulingConnector]
  private val dataMigrationConnector = mock[DataMigrationJsonConnector]
  private val dataMigrationService   = mock[DataMigrationService]
  private val appConfig              = mock[AppConfig]

  private def withService(test: ResetService => Any) =
    test(
      new ResetService(
        uploadRepository       = uploadRepository,
        fileConnector          = fileConnector,
        rulingConnector        = rulingConnector,
        caseConnector          = caseConnector,
        dataMigrationConnector = dataMigrationConnector,
        dataMigrationService   = dataMigrationService,
        appConfig              = appConfig
      )
    )

  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(
      uploadRepository,
      caseConnector,
      fileConnector,
      rulingConnector,
      dataMigrationConnector,
      dataMigrationService,
      appConfig
    )
  }

  override protected def beforeEach(): Unit =
    super.beforeEach()

  "Service 'Clear Environment'" should {
    val stores = Store.values

    "Throw an UnauthorizedException" in withService { service =>
      given(appConfig.resetPermitted) willReturn false

      assertThrows[UnauthorizedException] {
        await(service.resetEnvironment(stores))
      }

      verify(uploadRepository, never()).deleteAll()
      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService, never()).clear(refEq(None))
    }

    "Clear Back Ends" in withService { service =>
      given(appConfig.resetPermitted) willReturn true
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Clear Files" in withService { service =>
      given(appConfig.resetPermitted) willReturn true
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.FILES)))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService, never()).clear(any[Option[MigrationStatus]])
    }

    "Clear Cases" in withService { service =>
      given(appConfig.resetPermitted) willReturn true
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.CASES)))

      verify(uploadRepository, never()).deleteAll()
      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService, never()).clear(any[Option[MigrationStatus]])
    }

    "Clear Events" in withService { service =>
      given(appConfig.resetPermitted) willReturn true
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.EVENTS)))

      verify(uploadRepository, never()).deleteAll()
      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService, never()).clear(any[Option[MigrationStatus]])
    }

    "Clear Rulings" in withService { service =>
      given(appConfig.resetPermitted) willReturn true
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.RULINGS)))

      verify(uploadRepository, never()).deleteAll()
      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService, never()).clear(any[Option[MigrationStatus]])
    }

    "Clear Historic Data" in withService { service =>
      given(appConfig.resetPermitted) willReturn true
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)

      await(service.resetEnvironment(Set(Store.HISTORIC_DATA)))

      verify(uploadRepository, never()).deleteAll()
      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService, never()).clear(any[Option[MigrationStatus]])
    }

    "Clear Migrations" in withService { service =>
      given(appConfig.resetPermitted) willReturn true
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(Set(Store.MIGRATION)))

      verify(uploadRepository, never()).deleteAll()
      verify(fileConnector, never()).delete()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteCases()(any[HeaderCarrier])
      verify(caseConnector, never()).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector, never()).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector, never()).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Handle UploadRepository Failure" in withService { service =>
      given(appConfig.resetPermitted) willReturn true
      given(uploadRepository.deleteAll()) willReturn Future.failed(new RuntimeException("Error"))
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Handle FileStore Failure" in withService { service =>
      given(appConfig.resetPermitted) willReturn true
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Handle CaseStore Case Delete Failure " in withService { service =>
      given(appConfig.resetPermitted) willReturn true
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Handle CaseStore Event Delete Failure" in withService { service =>
      given(appConfig.resetPermitted) willReturn true
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Handle Migrations Delete Failure" in withService { service =>
      given(appConfig.resetPermitted) willReturn true
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationService.clear(refEq(None))) willReturn Future.failed(new RuntimeException("Error"))

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Handle Ruling Delete Failure" in withService { service =>
      given(appConfig.resetPermitted) willReturn true
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.failed(new RuntimeException("Error"))
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }

    "Handle Historic Data Delete Failure" in withService { service =>
      given(appConfig.resetPermitted) willReturn true
      given(uploadRepository.deleteAll()) willReturn Future.successful((): Unit)
      given(fileConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteCases()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(caseConnector.deleteEvents()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(rulingConnector.delete()(any[HeaderCarrier])) willReturn Future.successful((): Unit)
      given(dataMigrationConnector.deleteHistoricData()(any[HeaderCarrier])) willReturn Future.failed(
        new RuntimeException("Error")
      )
      given(dataMigrationService.clear(refEq(None))) willReturn Future.successful(true)

      await(service.resetEnvironment(stores))

      verify(uploadRepository).deleteAll()
      verify(fileConnector).delete()(any[HeaderCarrier])
      verify(caseConnector).deleteCases()(any[HeaderCarrier])
      verify(caseConnector).deleteEvents()(any[HeaderCarrier])
      verify(rulingConnector).delete()(any[HeaderCarrier])
      verify(dataMigrationConnector).deleteHistoricData()(any[HeaderCarrier])
      verify(dataMigrationService).clear(refEq(None))
    }
  }
}
