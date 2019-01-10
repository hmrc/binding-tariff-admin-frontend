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

package uk.gov.hmrc.bindingtariffadminfrontend.repository

import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import reactivemongo.api.DB
import reactivemongo.bson._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.{CaseMigration, Cases, MigrationStatus}
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global

class CaseMigrationRepositorySpec extends BaseMongoIndexSpec
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with MongoSpecSupport
  with Eventually
  with MockitoSugar {
  self =>

  private val mongoDbProvider: MongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private val config = mock[AppConfig]
  private val repository = new CaseMigrationMongoRepository(config, mongoDbProvider)

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(repository.drop)
  }

  private def collectionSize: Int = {
    await(repository.collection.count())
  }

  "getAll" should {
    val aCase = Cases.btiCaseExample
    val migration = CaseMigration(aCase, MigrationStatus.SUCCESS)

    "retrieve the expected documents from the collection" in {

      await(repository.insert(migration))
      collectionSize shouldBe 1

      await(repository.get()) contains Seq(migration)
    }

    "return None when there are no documents in the collection" in {
      await(repository.get()) shouldBe Seq.empty
    }

  }

  private def selectorById(migration: CaseMigration) = {
    BSONDocument("id" -> migration.`case`.reference)
  }

}
