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
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.{Cases, Migration, MigrationStatus}
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global

class MigrationRepositorySpec extends BaseMongoIndexSpec
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
  private val repository = new MigrationMongoRepository(config, mongoDbProvider)

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
    val aCase = Cases.migratableCase
    val migration = Migration(aCase)

    "retrieve the expected documents from the collection" in {

      await(repository.insert(migration))
      collectionSize shouldBe 1

      await(repository.get()) contains Seq(migration)
    }

    "return None when there are no documents in the collection" in {
      await(repository.get()) shouldBe Seq.empty
    }

  }

  "get by id" should {
    val aCase = Cases.migratableCase
    val migration = Migration(aCase)

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

  "get by status" should {
    val aCase = Cases.migratableCase
    val migration = Migration(aCase, status = MigrationStatus.UNPROCESSED)

    "retrieves the expected document" in {
      await(repository.insert(migration))
      collectionSize shouldBe 1

      await(repository.get(migration.status)) shouldBe Some(migration)
    }

    "retrieves None when the status is not found" in {
      await(repository.insert(migration))
      collectionSize shouldBe 1

      await(repository.get(MigrationStatus.SUCCESS)) shouldBe None
    }
  }

  "update" should {
    val aCase = Cases.migratableCase
    val migration = Migration(aCase)

    "modify an existing document in the collection" in {
      await(repository.insert(migration))
      collectionSize shouldBe 1

      val updated: Migration = migration.copy(status = MigrationStatus.SUCCESS)
      await(repository.update(updated)) shouldBe Some(updated)

      collectionSize shouldBe 1
      await(repository.collection.find(selectorByReference(updated)).one[Migration]) shouldBe Some(updated)
    }

    "do nothing when trying to update a non existing document in the collection" in {
      val size = collectionSize

      await(repository.update(migration)) shouldBe None

      collectionSize shouldBe size
    }
  }

  "insert" should {
    val migrations = Seq(Migration(Cases.migratableCase),
                         Migration(Cases.migratableCase.copy(reference = "2")))

    "insert a list of new documents into the collection" in {
      val size = collectionSize

      await(repository.insert(migrations)) shouldBe true

      collectionSize shouldBe size + 2
      await(repository.collection.find(selectorByReference(migrations.head)).one[Migration]) shouldBe Some(migrations.head)
      await(repository.collection.find(selectorByReference(migrations(1))).one[Migration]) shouldBe Some(migrations(1))
    }
  }

  "delete one" should {
    val migrations = Seq(Migration(Cases.migratableCase),
      Migration(Cases.migratableCase.copy(reference = "2")))

    "removes document from the collection" in {
      await(repository.insert(migrations)) shouldBe true
      val size = collectionSize

      await(repository.delete(migrations.head))

      collectionSize shouldBe size - 1
      await(repository.collection.find(selectorByReference(migrations.head)).one[Migration]) shouldBe None
      await(repository.collection.find(selectorByReference(migrations(1))).one[Migration]) shouldBe Some(migrations(1))
    }
  }

  "delete all" should {
    val migrations = Seq(Migration(Cases.migratableCase),
      Migration(Cases.migratableCase.copy(reference = "2")))

    "remove documents from the collection" in {
      await(repository.insert(migrations)) shouldBe true

      await(repository.delete(None)) shouldBe true

      collectionSize shouldBe 0
    }
  }

  "delete by status" should {
    val migrations = Seq(
      Migration(Cases.migratableCase.copy(reference = "1"), status = MigrationStatus.FAILED),
      Migration(Cases.migratableCase.copy(reference = "2"), status = MigrationStatus.SUCCESS)
    )

    "remove documents from the collection" in {
      await(repository.insert(migrations)) shouldBe true
      collectionSize shouldBe 2

      await(repository.delete(Some(MigrationStatus.FAILED))) shouldBe true

      collectionSize shouldBe 1
      await(repository.collection.find(selectorByReference(migrations(1))).one[Migration]) shouldBe Some(migrations(1))
    }
  }

  "countByStatus" should {
    val aCase = Cases.migratableCase
    val migration1 = Migration(aCase.copy(reference = "1"), MigrationStatus.SUCCESS)
    val migration2 = Migration(aCase.copy(reference = "2"), MigrationStatus.SUCCESS)
    val migration3 = Migration(aCase.copy(reference = "3"), MigrationStatus.FAILED)

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

  "The 'cases' collection" should {
    val aCase = Migration(Cases.migratableCase)

    "have a unique index based on the field 'reference' " in {
      await(repository.insert(aCase))
      val size = collectionSize

      val caught = intercept[DatabaseException] {
        await(repository.insert(aCase.copy(status = MigrationStatus.SUCCESS)))
      }
      caught.code shouldBe Some(11000)

      collectionSize shouldBe size
    }
  }

  private def selectorByReference(caseMigration: Migration) = {
    BSONDocument("case.reference" -> caseMigration.`case`.reference)
  }

}
