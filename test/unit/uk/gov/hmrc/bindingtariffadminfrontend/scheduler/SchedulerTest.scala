/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffadminfrontend.scheduler

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Cancellable}
import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.play.scheduling.ScheduledJob
import uk.gov.hmrc.bindingtariffadminfrontend.util.UnitSpec

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class SchedulerTest extends UnitSpec with MockitoSugar {

  private val actorSystem       = mock[ActorSystem]
  private val internalScheduler = mock[akka.actor.Scheduler]
  private val job               = mock[ScheduledJob]

  "Scheduler" should {
    val initialDelay = FiniteDuration(1, TimeUnit.SECONDS)
    val interval     = FiniteDuration(2, TimeUnit.SECONDS)

    "Schedule Job" in {
      given(actorSystem.scheduler) willReturn internalScheduler
      given(internalScheduler.schedule(any[FiniteDuration], any[FiniteDuration], any[Runnable])(any[ExecutionContext])) will runTheJobImmediately
      given(job.initialDelay) willReturn initialDelay
      given(job.interval) willReturn interval
      given(job.execute(any[ExecutionContext])) willReturn Future.successful(null)

      // When
      new Scheduler(actorSystem, job)

      // Then
      verify(internalScheduler).schedule(refEq(initialDelay), refEq(interval), any[Runnable])(any[ExecutionContext])
      verify(job).execute(any[ExecutionContext])
    }
  }

  private def runTheJobImmediately: Answer[Cancellable] = new Answer[Cancellable] {
    override def answer(invocation: InvocationOnMock): Cancellable = {
      val arg: Runnable = invocation.getArgument(2)
      if (arg != null) {
        arg.run()
      }
      Cancellable.alreadyCancelled
    }
  }

}
