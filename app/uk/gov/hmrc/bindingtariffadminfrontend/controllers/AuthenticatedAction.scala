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

package uk.gov.hmrc.bindingtariffadminfrontend.controllers

import java.security.MessageDigest
import java.time.LocalDate

import com.google.common.io.BaseEncoding
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.mvc.Results._
import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, BodyParsers, Request, Result}
import play.mvc.Http.HeaderNames.{AUTHORIZATION, WWW_AUTHENTICATE}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.{AuthenticatedRequest, Credentials}
import uk.gov.hmrc.bindingtariffadminfrontend.views.html.password_expired

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class AuthenticatedAction @Inject()(
                                     appConfig: AppConfig,
                                     bodyParser: BodyParsers.Default
                                   ) extends ActionBuilder[AuthenticatedRequest, AnyContent] {

  private lazy val year: Int = LocalDate.now(appConfig.clock).getYear
  private lazy val credentials: Seq[Credentials] = appConfig.credentials

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    def unauthorized: Future[Result] = Future.successful(Unauthorized.withHeaders(WWW_AUTHENTICATE -> "Basic realm=Unauthorized"))

    request.headers.get(AUTHORIZATION)
      .map(decode)
      .filter(_.isSuccess)
      .map(_.get)
      .map {
        // Valid Auth
        case (username, password) if credentials.contains(Credentials(username, sha256(year, password))) =>
          block(AuthenticatedRequest(username, request))

        // Valid Auth but the password has recently expired
        case (username, password) if credentials.contains(Credentials(username, sha256(year - 1, password))) =>
          Logger.error("The service password has expired. Please generate a new one using sha256(year:password) and override configuration key `auth.credentials`")
          Future.successful(Ok(password_expired()))

        // Invalid Auth
        case _ =>
          unauthorized
      }
      .getOrElse(unauthorized)
  }

  private def decode(authorization: String): Try[(String, String)] = Try {
    val baStr = authorization.replaceFirst("Basic ", "")
    val decoded = BaseEncoding.base64().decode(baStr)
    val Array(user, password) = new String(decoded).split(":")
    (user, password)
  }

  private def sha256(year: Int, password: String): String = sha256(year + ":" + password)

  private def sha256(value: String): String = {
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes("UTF-8"))
      .map("%02x".format(_)).mkString.toUpperCase()
  }

  override def parser: BodyParser[AnyContent] = bodyParser

  override protected def executionContext: ExecutionContext = global
}
