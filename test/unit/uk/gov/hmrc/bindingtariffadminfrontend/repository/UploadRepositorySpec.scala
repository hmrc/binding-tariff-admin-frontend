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

package uk.gov.hmrc.bindingtariffadminfrontend.repository

import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.DB
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.{AttachmentUpload, HistoricDataUpload, MigrationDataUpload, Upload}
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global

class UploadRepositorySpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MongoSpecSupport
    with Eventually
    with MockitoSugar {
  self =>

  private val mongoDbProvider: MongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private val config     = mock[AppConfig]
  private val repository = new UploadMongoRepository(config, mongoDbProvider)

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(repository.drop)
  }

  private def collectionSize: Int =
    await(repository.collection.count())

  private val upload1 =
    AttachmentUpload(fileName = "filename1", mimeType = "text/plain", id = "id1", batchId = "batchId1")
  private val upload2 =
    AttachmentUpload(fileName = "filename2", mimeType = "text/plain", id = "id2", batchId = "batchId1")
  private val upload3 =
    MigrationDataUpload(fileName = "filename3", mimeType = "text/plain", id = "id3", batchId = "batchId2")
  private val upload4 =
    MigrationDataUpload(fileName = "filename4", mimeType = "text/plain", id = "id4", batchId = "batchId2")
  private val upload5 =
    HistoricDataUpload(fileName = "filename5", mimeType = "text/plain", id = "id5", batchId = "batchId3")

  "getByFileName" should {
    "retrieve the expected document" in {
      await(repository.insert(upload1))
      collectionSize shouldBe 1

      await(repository.getByFileName(upload1.fileName)) shouldBe Some(upload1)
    }

    "retrieves None when the filename is not found" in {
      await(repository.insert(upload1))
      collectionSize shouldBe 1

      await(repository.getByFileName("WRONG_FILENAME")) shouldBe None
    }
  }

  "getByFileNames" should {
    "retrieve the expected documents" in {
      await(repository.bulkInsert(Seq(upload1, upload2, upload3)))
      collectionSize shouldBe 3

      await(repository.getByFileNames(List(upload1.fileName, upload3.fileName, "WRONG_FILENAME"))) shouldBe List(
        upload1,
        upload3
      )
    }

    "retrieves Nil when the filenames are not found" in {
      await(repository.bulkInsert(Seq(upload1, upload2)))
      collectionSize shouldBe 2

      await(repository.getByFileNames(List("WRONG_FILENAME"))) shouldBe Nil
    }
  }

  "getByBatch" should {
    "retrieves the expected documents" in {
      await(repository.bulkInsert(Seq(upload1, upload2, upload3, upload4, upload5)))
      collectionSize shouldBe 5

      await(repository.getByBatch("batchId1")) shouldBe List(upload1, upload2)
    }

    "retrieves Nil when the batchId is not found" in {
      await(repository.bulkInsert(Seq(upload1, upload2, upload3, upload4, upload5)))
      collectionSize shouldBe 5

      await(repository.getByBatch("WRONG_BATCH_ID")) shouldBe Nil
    }
  }

  "getByType" should {
    "retrieves the expected documents" in {
      await(repository.bulkInsert(Seq(upload1, upload2, upload3, upload4, upload5)))
      collectionSize shouldBe 5

      await(repository.getByType[AttachmentUpload])    shouldBe List(upload1, upload2)
      await(repository.getByType[MigrationDataUpload]) shouldBe List(upload3, upload4)
      await(repository.getByType[HistoricDataUpload])  shouldBe List(upload5)
    }

    "retrieves None when the type is not found" in {
      await(repository.bulkInsert(Seq(upload1, upload2)))
      collectionSize shouldBe 2

      await(repository.getByType[MigrationDataUpload]) shouldBe Nil
    }
  }

  "update" should {
    "modify an existing document in the collection" in {
      await(repository.insert(upload1))
      collectionSize shouldBe 1

      val updated = upload1.copy(id = "anotherId")
      await(repository.update(updated)) shouldBe Some(updated)

      collectionSize                                                              shouldBe 1
      await(repository.collection.find(byFileName(updated.fileName)).one[Upload]) shouldBe Some(updated)
    }

    "upsert if there is no existing document" in {
      collectionSize                                                              shouldBe 0
      await(repository.update(upload1))                                           shouldBe Some(upload1)
      collectionSize                                                              shouldBe 1
      await(repository.collection.find(byFileName(upload1.fileName)).one[Upload]) shouldBe Some(upload1)
    }
  }

  "delete all" should {
    "remove documents from the collection" in {
      await(repository.bulkInsert(Seq(upload1, upload2, upload3, upload4, upload5)))
      collectionSize shouldBe 5

      await(repository.deleteAll())
      collectionSize shouldBe 0
    }
  }

  "delete by id" should {
    "remove specified documents from the collection" in {
      await(repository.bulkInsert(Seq(upload1, upload2, upload3, upload4, upload5)))
      collectionSize shouldBe 5

      await(repository.deleteById(upload2.id))
      collectionSize shouldBe 4

      await(repository.collection.find(byId(upload1.id)).one[Upload]) shouldBe Some(upload1)
      await(repository.collection.find(byId(upload2.id)).one[Upload]) shouldBe None
      await(repository.collection.find(byId(upload3.id)).one[Upload]) shouldBe Some(upload3)
      await(repository.collection.find(byId(upload4.id)).one[Upload]) shouldBe Some(upload4)
      await(repository.collection.find(byId(upload5.id)).one[Upload]) shouldBe Some(upload5)
    }
  }

  "The 'uploads' collection" should {
    "have a unique index based on the field 'fileName'" in {
      await(repository.insert(upload1))
      val size = collectionSize

      val caught = intercept[DatabaseException] {
        await(repository.insert(upload5.copy(fileName = upload1.fileName)))
      }
      caught.code shouldBe Some(11000)

      collectionSize shouldBe size
    }

    "have a unique index based on the field 'id'" in {
      await(repository.insert(upload1))
      val size = collectionSize

      val caught = intercept[DatabaseException] {
        await(repository.insert(upload5.copy(id = upload1.id)))
      }
      caught.code shouldBe Some(11000)

      collectionSize shouldBe size
    }
  }

  private def byFileName(fileName: String): JsObject =
    Json.obj("fileName" -> fileName)

  private def byId(id: String): JsObject =
    Json.obj("id" -> id)
}
