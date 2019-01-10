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
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.bson._
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigrationStatus.MigrationStatus
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
    val migration = CaseMigration(aCase)

    "retrieve the expected documents from the collection" in {

      await(repository.insert(migration))
      collectionSize shouldBe 1

      await(repository.get()) contains Seq(migration)
    }

    "return None when there are no documents in the collection" in {
      await(repository.get()) shouldBe Seq.empty
    }

    "retrieve the expected documents from the collection by status" in {

      await(repository.insert(migration))
      collectionSize shouldBe 1

      await(repository.get(Some(MigrationStatus.SUCCESS))) contains Seq(migration)
      await(repository.get(Some(MigrationStatus.FAILED))) contains Seq.empty
    }

  }

  "get by id" should {
    val aCase = Cases.btiCaseExample
    val migration = CaseMigration(aCase)

    "retrieves the expected document" in {
      await(repository.insert(migration))
      collectionSize shouldBe 1

      await(repository.get(aCase.reference)) shouldBe Some(migration)
    }

    "retrieves None when the case reference is not found" in {
      await(repository.insert(migration))
      collectionSize shouldBe 1

      await(repository.get("WRONG_REFERENCE")) shouldBe None
    }
  }

  "update" should {
    val aCase = Cases.btiCaseExample
    val migration = CaseMigration(aCase)

    "modify an existing document in the collection" in {
      await(repository.insert(migration))
      collectionSize shouldBe 1

      val updated: CaseMigration = migration.copy(status = MigrationStatus.SUCCESS)
      await(repository.update(updated)) shouldBe Some(updated)

      collectionSize shouldBe 1
      await(repository.collection.find(selectorByReference(updated)).one[CaseMigration]) shouldBe Some(updated)
    }

    "do nothing when trying to update a non existing document in the collection" in {
      val size = collectionSize

      await(repository.update(migration)) shouldBe None

      collectionSize shouldBe size
    }
  }

  "insert" should {
    val migrations = Seq(CaseMigration(Cases.btiCaseExample),
                         CaseMigration(Cases.btiCaseExample.copy(reference = "2")))

    "insert a list of new documents into the collection" in {
      val size = collectionSize

      await(repository.insert(migrations)) shouldBe true

      collectionSize shouldBe size + 2
      await(repository.collection.find(selectorByReference(migrations.head)).one[CaseMigration]) shouldBe Some(migrations.head)
      await(repository.collection.find(selectorByReference(migrations(1))).one[CaseMigration]) shouldBe Some(migrations(1))
    }
  }

  "delete" should {
    val migrations = Seq(CaseMigration(Cases.btiCaseExample),
      CaseMigration(Cases.btiCaseExample.copy(reference = "2")))

    "removes document from the collection" in {
      await(repository.insert(migrations)) shouldBe true
      val size = collectionSize

      await(repository.delete(migrations.head))

      collectionSize shouldBe size - 1
      await(repository.collection.find(selectorByReference(migrations.head)).one[CaseMigration]) shouldBe None
      await(repository.collection.find(selectorByReference(migrations(1))).one[CaseMigration]) shouldBe Some(migrations(1))
    }
  }

  "countByStatus" should {
    val aCase = Cases.btiCaseExample
    val migration1 = CaseMigration(aCase, MigrationStatus.SUCCESS)
    val migration2 = CaseMigration(aCase, MigrationStatus.SUCCESS)
    val migration3 = CaseMigration(aCase, MigrationStatus.FAILED)

    "collect counts" in {
      await(repository.insert(migration1))
      await(repository.insert(migration2))
      await(repository.insert(migration3))
      collectionSize shouldBe 3

      val value = await(repository.countByStatus)
      value.get(MigrationStatus.SUCCESS) shouldBe 2
      value.get(MigrationStatus.FAILED) shouldBe 1
      value.total shouldBe 3
    }
  }

  private def selectorByReference(caseMigration: CaseMigration) = {
    BSONDocument("case.reference" -> caseMigration.`case`.reference)
  }

}
