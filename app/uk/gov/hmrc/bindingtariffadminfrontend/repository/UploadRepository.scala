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

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsObject, Json, OFormat}
import reactivemongo.api.Cursor
import reactivemongo.api.indexes.Index
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.Upload
import uk.gov.hmrc.bindingtariffadminfrontend.repository.MongoIndexCreator.createSingleFieldAscendingIndex
import uk.gov.hmrc.bindingtariffadminfrontend.util.JsonUtil
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.{ClassTag, classTag}

@ImplementedBy(classOf[UploadMongoRepository])
trait UploadRepository {
  def getByFileName(fileName: String): Future[Option[Upload]]

  def getByFileNames(fileNames: List[String]): Future[List[Upload]]

  def getByBatch(batchId: String): Future[List[Upload]]

  def getByType[T <: Upload: ClassTag]: Future[List[Upload]]

  def update(upload: Upload): Future[Option[Upload]]

  def deleteAll(): Future[Unit]
}

@Singleton
class UploadMongoRepository @Inject() (config: AppConfig, mongoDbProvider: MongoDbProvider)
    extends ReactiveRepository[Upload, BSONObjectID](
      collectionName = "Uploads",
      mongo          = mongoDbProvider.mongo,
      domainFormat   = JsonUtil.oFormatOf(Upload.format)
    )
    with UploadRepository {

  implicit val uploadRequestFormat: OFormat[Upload] = JsonUtil.oFormatOf(Upload.format)

  override lazy val indexes: Seq[Index] = Seq(
    createSingleFieldAscendingIndex("id", isUnique       = true),
    createSingleFieldAscendingIndex("fileName", isUnique = true),
    createSingleFieldAscendingIndex("batchId", isUnique  = false)
  )

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(indexes.map(collection.indexesManager(ec).ensure(_)))(implicitly, ec)

  override def getByFileName(fileName: String): Future[Option[Upload]] =
    collection
      .find[JsObject, Upload](byFileName(fileName))
      .one[Upload]

  override def getByFileNames(fileNames: List[String]): Future[List[Upload]] =
    collection
      .find[JsObject, Upload](byFileNames(fileNames))
      .cursor[Upload]()
      .collect[List](Int.MaxValue, Cursor.FailOnError[List[Upload]]())

  override def getByBatch(batchId: String): Future[List[Upload]] =
    collection
      .find[JsObject, Upload](byBatchId(batchId))
      .cursor[Upload]()
      .collect[List](Int.MaxValue, Cursor.FailOnError[List[Upload]]())

  override def getByType[T <: Upload: ClassTag]: Future[List[Upload]] =
    collection
      .find[JsObject, Upload](byType[T])
      .cursor[Upload]()
      .collect[List](Int.MaxValue, Cursor.FailOnError[List[Upload]]())

  override def update(upload: Upload): Future[Option[Upload]] =
    collection
      .findAndUpdate(
        selector = byFileName(upload.fileName),
        update   = upload,
        upsert   = true
      )
      .map(_.value.map(_.as[Upload]))

  private def byFileName(fileName: String): JsObject =
    Json.obj("fileName" -> fileName)

  private def byFileNames(fileNames: List[String]): JsObject =
    Json.obj("fileName" -> Json.obj("$in" -> fileNames))

  private def byType[T <: Upload: ClassTag]: JsObject =
    Json.obj("type" -> classTag[T].runtimeClass.getSimpleName)

  private def byBatchId(batchId: String): JsObject =
    Json.obj("batchId" -> batchId)

  override def deleteAll(): Future[Unit] =
    collection.delete().one(Json.obj(), None).map(_ => ())
}
