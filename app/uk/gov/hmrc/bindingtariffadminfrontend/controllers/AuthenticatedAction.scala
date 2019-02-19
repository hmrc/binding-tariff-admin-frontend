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

package uk.gov.hmrc.bindingtariffadminfrontend.controllers

import java.security.MessageDigest

import com.google.common.io.BaseEncoding
import javax.inject.{Inject, Singleton}
import play.api.mvc.Results._
import play.api.mvc.{ActionBuilder, Request, Result}
import play.mvc.Http.HeaderNames.{AUTHORIZATION, WWW_AUTHENTICATE}
import uk.gov.hmrc.bindingtariffadminfrontend.config.AppConfig
import uk.gov.hmrc.bindingtariffadminfrontend.model.{AuthenticatedRequest, Credentials}

import scala.concurrent.Future
import scala.util.Try

@Singleton
class AuthenticatedAction @Inject()(appConfig: AppConfig) extends ActionBuilder[AuthenticatedRequest] {

  private lazy val credentials: Seq[Credentials] = appConfig.credentials

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    request.headers.get(AUTHORIZATION)
      .map(decode)
      .filter(_.isSuccess)
      .filter(decoded => credentials.contains(decoded.get))
      .map(c => block(AuthenticatedRequest(c.get.username, request)))
      .getOrElse(Future.successful(Unauthorized.withHeaders(WWW_AUTHENTICATE -> "Basic realm=Unauthorized")))
  }

  private def decode(authorization: String): Try[Credentials] = Try {
    val baStr = authorization.replaceFirst("Basic ", "")
    val decoded = BaseEncoding.base64().decode(baStr)
    val Array(user, password) = new String(decoded).split(":")
    Credentials(user, sha256(password))
  }

  private def sha256(value: String): String = {
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes("UTF-8"))
      .map("%02x".format(_)).mkString.toUpperCase()
  }

}
