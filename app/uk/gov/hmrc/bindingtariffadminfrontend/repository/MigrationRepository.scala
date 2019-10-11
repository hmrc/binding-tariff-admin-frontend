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
import play.api.libs.json.{JsObject, JsString, Json}
import reactivemongo.api.indexes.Index
import reactivemongo.api.{Cursor, QueryOpts}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.MigrationStatus.MigrationStatus
import uk.gov.hmrc.bindingtariffadminfrontend.model.{Migration, MigrationCounts, Paged, Pagination}
import uk.gov.hmrc.bindingtariffadminfrontend.repository.MongoIndexCreator.createSingleFieldAscendingIndex
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MigrationMongoRepository])
trait MigrationRepository {

  def countByStatus: Future[MigrationCounts]

  def get(reference: String): Future[Option[Migration]]

  def get(status: MigrationStatus): Future[Option[Migration]]

  def get(status: Seq[MigrationStatus], pagination: Pagination): Future[Paged[Migration]]

  def update(c: Migration): Future[Option[Migration]]

  def insert(c: Seq[Migration]): Future[Boolean]

  def delete(c: Migration): Future[Boolean]

  def delete(c: Seq[Migration]): Future[Boolean]

  def delete(status: Option[MigrationStatus]): Future[Boolean]

}

@Singleton
class MigrationMongoRepository @Inject()(config: AppConfig,
                                         mongoDbProvider: MongoDbProvider)
  extends ReactiveRepository[Migration, BSONObjectID](
    collectionName = "CaseMigration",
    mongo = mongoDbProvider.mongo,
    domainFormat = Migration.Mongo.format) with MigrationRepository {
  import Migration.Mongo.format

  override lazy val indexes: Seq[Index] = Seq(
    createSingleFieldAscendingIndex("case.reference", isUnique = true),
    createSingleFieldAscendingIndex("status", isUnique = false)
  )

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    Future.sequence(indexes.map(collection.indexesManager.ensure(_)))
  }

  override def get(status: Seq[MigrationStatus], pagination: Pagination): Future[Paged[Migration]] = {
    val filter = if (status.isEmpty) Json.obj() else Json.obj("status" -> Json.obj("$in" -> status))
    val actualPage = if(pagination.page > 1) pagination.page else 1
    val query = Json.obj("status" -> -1)
    for {
      result <- collection
        .find(filter)
        .sort(query)
        .options(QueryOpts(skipN = (actualPage - 1) * pagination.pageSize, batchSizeN = pagination.pageSize))
        .cursor[Migration]()
        .collect[Seq](pagination.pageSize, Cursor.FailOnError[Seq[Migration]]())
      count <- collection.count(Some(filter))
    } yield Paged(result, pagination, count)
  }

  override def get(reference: String): Future[Option[Migration]] = {
    collection.find(byReference(reference)).one[Migration]
  }

  override def get(status: MigrationStatus): Future[Option[Migration]] = {
    collection.find(byStatus(status)).one[Migration]
  }

  override def update(c: Migration): Future[Option[Migration]] = {
    collection.findAndUpdate(
      selector = byReference(c.`case`.reference),
      update = c,
      fetchNewObject = true
    ).map(_.value.map(_.as[Migration]))
  }

  override def delete(c: Migration): Future[Boolean] = {
    collection.remove(byReference(c.`case`.reference)).map(_.ok)
  }

  override def insert(c: Seq[Migration]): Future[Boolean] = {
    val producers = c.map(implicitly[collection.ImplicitlyDocumentProducer](_))
    collection.bulkInsert(ordered = false)(producers: _*).map{a =>
      Logger.error("BTI mongo error message ::::::: " + a.errmsg)
      a.ok
    }
  }

  def countByStatus: Future[MigrationCounts] = {

    import collection.BatchCommands.AggregationFramework.{Group, SumAll}

    val group = Group(JsString("$status"))("count" -> SumAll)
    collection.aggregate(group).map(_.firstBatch.map { json =>
      val status = json.value("_id").as[MigrationStatus]
      val count = json.value("count").as[Int]
      (status, count)
    }).map(list => new MigrationCounts(list.toMap))
  }

  override def delete(status: Option[MigrationStatus]): Future[Boolean] = {
    val query = status.map(s => Json.obj("status" -> s))
    query match {
      case Some(_) => collection.remove(query.get).map(_.ok)
      case _ => removeAll().map(_.ok)
    }
  }

  override def delete(migrations: Seq[Migration]): Future[Boolean] = {
    val references = migrations.map(_.`case`.reference)

    val query = Json.obj("case.reference" -> Json.obj("$in" -> references))
    collection.remove(query).map(_.ok)
  }

  private def byReference(reference: String): JsObject = {
    Json.obj("case.reference" -> reference)
  }

  private def byStatus(status: MigrationStatus): JsObject = {
    Json.obj("status" -> status)
  }

}
