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

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.Cursor
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.{CaseMigration, MigrationStatus}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.MongoIndexCreator._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[CaseMigrationMongoRepository])
trait CaseMigrationRepository {

  def containsUnprocessedEntities: Future[Boolean]

  def get(id: String): Future[Option[CaseMigration]]

  def get(): Future[Seq[CaseMigration]]

  def update(c: CaseMigration): Future[CaseMigration]

  def insert(c: Seq[CaseMigration]): Future[Boolean]

  def delete(c: CaseMigration): Future[Boolean]

}

@Singleton
class CaseMigrationMongoRepository @Inject()(config: AppConfig,
                                             mongoDbProvider: MongoDbProvider)
  extends ReactiveRepository[CaseMigration, BSONObjectID](
    collectionName = "CaseMigration",
    mongo = mongoDbProvider.mongo,
    domainFormat = CaseMigration.format,
    idFormat = ReactiveMongoFormats.objectIdFormats) with CaseMigrationRepository {

  override lazy val indexes = Seq(
    createSingleFieldAscendingIndex("id", isUnique = true)
  )

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    Future.sequence(indexes.map(collection.indexesManager.ensure(_)))
  }

  override def get(): Future[Seq[CaseMigration]] = {
    collection.find(Json.obj())
      .cursor[CaseMigration]()
      .collect[Seq](-1, Cursor.FailOnError[Seq[CaseMigration]]())
  }

  override def get(id: String): Future[Option[CaseMigration]] = {
    collection.find(byReference(id)).one[CaseMigration]
  }

  override def update(c: CaseMigration): Future[CaseMigration] = {
    collection.update(
      selector = byReference(c.`case`.reference),
      update = c
    ).map(_ => c)
  }

  override def delete(c: CaseMigration): Future[Boolean] = {
    collection.remove(byReference(c.`case`.reference)).map(_.ok)
  }

  override def insert(c: Seq[CaseMigration]): Future[Boolean] = {
    val producers = c.map(implicitly[collection.ImplicitlyDocumentProducer](_))
    collection.bulkInsert(ordered = false)(producers: _*).map(_.ok)
  }

  def containsUnprocessedEntities: Future[Boolean] = {
    collection.count(Some(Json.obj("status" -> MigrationStatus.UNPROCESSED))).map(_ > 0)
  }

  private def byReference(id: String): JsObject = {
    Json.obj("reference" -> id)
  }
}
