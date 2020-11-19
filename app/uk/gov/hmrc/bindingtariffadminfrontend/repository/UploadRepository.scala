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
import uk.gov.hmrc.bindingtariffadminfrontend.model.filestore.UploadRequest
import uk.gov.hmrc.bindingtariffadminfrontend.repository.MongoIndexCreator.createSingleFieldAscendingIndex
import uk.gov.hmrc.bindingtariffadminfrontend.util.JsonUtil
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.{ClassTag, classTag}

@ImplementedBy(classOf[UploadMongoRepository])
trait UploadRepository {
  def getByFileName(fileName: String): Future[Option[UploadRequest]]

  def getByFileNames(fileNames: List[String]): Future[List[UploadRequest]]

  def getByBatch(batchId: String): Future[List[UploadRequest]]

  def getByType[T <: UploadRequest: ClassTag]: Future[List[UploadRequest]]

  def update(upload: UploadRequest): Future[Option[UploadRequest]]

  def deleteAll(): Future[Unit]
}

@Singleton
class UploadMongoRepository @Inject() (config: AppConfig, mongoDbProvider: MongoDbProvider)
    extends ReactiveRepository[UploadRequest, BSONObjectID](
      collectionName = "Uploads",
      mongo          = mongoDbProvider.mongo,
      domainFormat   = JsonUtil.oFormatOf(UploadRequest.format)
    )
    with UploadRepository {

  implicit val uploadRequestFormat: OFormat[UploadRequest] = JsonUtil.oFormatOf(UploadRequest.format)

  override lazy val indexes: Seq[Index] = Seq(
    createSingleFieldAscendingIndex("id", isUnique       = true),
    createSingleFieldAscendingIndex("fileName", isUnique = true),
    createSingleFieldAscendingIndex("batchId", isUnique  = false)
  )

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(indexes.map(collection.indexesManager(ec).ensure(_)))(implicitly, ec)

  override def getByFileName(fileName: String): Future[Option[UploadRequest]] =
    collection
      .find[JsObject, UploadRequest](byFileName(fileName))
      .one[UploadRequest]

  override def getByFileNames(fileNames: List[String]): Future[List[UploadRequest]] =
    collection
      .find[JsObject, UploadRequest](byFileNames(fileNames))
      .cursor[UploadRequest]()
      .collect[List](Int.MaxValue, Cursor.FailOnError[List[UploadRequest]]())

  override def getByBatch(batchId: String): Future[List[UploadRequest]] =
    collection
      .find[JsObject, UploadRequest](byBatchId(batchId))
      .cursor[UploadRequest]()
      .collect[List](Int.MaxValue, Cursor.FailOnError[List[UploadRequest]]())

  override def getByType[T <: UploadRequest: ClassTag]: Future[List[UploadRequest]] =
    collection
      .find[JsObject, UploadRequest](byType[T])
      .cursor[UploadRequest]()
      .collect[List](Int.MaxValue, Cursor.FailOnError[List[UploadRequest]]())

  override def update(upload: UploadRequest): Future[Option[UploadRequest]] =
    collection
      .findAndUpdate(
        selector = byFileName(upload.fileName),
        update   = upload,
        upsert   = true
      )
      .map(_.value.map(_.as[UploadRequest]))

  private def byFileName(fileName: String): JsObject =
    Json.obj("fileName" -> fileName)

  private def byFileNames(fileNames: List[String]): JsObject =
    Json.obj("fileName" -> Json.obj("$in" -> fileNames))

  private def byType[T <: UploadRequest: ClassTag]: JsObject =
    Json.obj("type" -> classTag[T].runtimeClass.getSimpleName)

  private def byBatchId(batchId: String): JsObject =
    Json.obj("batchId" -> batchId)

  override def deleteAll(): Future[Unit] =
    collection.delete().one(Json.obj(), None).map(_ => ())
}
