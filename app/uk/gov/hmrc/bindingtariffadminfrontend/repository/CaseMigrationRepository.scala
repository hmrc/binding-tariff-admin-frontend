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
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.Case
import uk.gov.hmrc.bindingtariffadminfrontend.repository.MongoIndexCreator._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[CaseMigrationMongoRepository])
trait CaseMigrationRepository {

  def get(id: String): Future[Option[Case]]

  def insert(c: Case): Future[Case]

  def insert(c: Seq[Case]): Future[Case]

  def delete(c: Case): Future[Boolean]

}

@Singleton
class CaseMigrationMongoRepository @Inject()(config: AppConfig,
                                             mongoDbProvider: MongoDbProvider)
  extends ReactiveRepository[Case, BSONObjectID](
    collectionName = "Case",
    mongo = mongoDbProvider.mongo,
    domainFormat = Case.format,
    idFormat = ReactiveMongoFormats.objectIdFormats) with CaseMigrationRepository {

  override lazy val indexes = Seq(
    createSingleFieldAscendingIndex("id", isUnique = true)
  )

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    Future.sequence(indexes.map(collection.indexesManager.ensure(_)))
  }

  override def get(id: String): Future[Option[Case]] = {
    collection.find(byReference(id)).one[Case]
  }

  override def insert(c: Case): Future[Case] = {
    collection.insert(c).map(_ => c)
  }

  override def delete(c: Case): Future[Boolean] = {
    collection.remove(byReference(c.reference)).map(_.ok)
  }

  private def byReference(id: String) = {
    Json.obj("reference" -> id)
  }

  override def insert(c: Seq[Case]): Future[Case] = ???
}
