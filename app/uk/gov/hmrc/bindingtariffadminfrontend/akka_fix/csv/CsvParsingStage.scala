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

package uk.gov.hmrc.bindingtariffadminfrontend.akka_fix.csv

/*
 * Copyright (C) 2016-2019 Lightbend Inc. <http://www.lightbend.com>
 */

import akka.annotation.InternalApi
import akka.event.Logging
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString

import scala.annotation.tailrec
import scala.util.control.NonFatal

/**
  * Internal API: Use [[akka.stream.alpakka.csv.scaladsl.CsvParsing]] instead.
  */
@InternalApi private[csv] class CsvParsingStage(delimiter: Byte,
                                                quoteChar: Byte,
                                                escapeChar: Byte,
                                                maximumLineLength: Int)
  extends GraphStage[FlowShape[ByteString, List[ByteString]]] {

  private val in = Inlet[ByteString](Logging.simpleName(this) + ".in")
  private val out = Outlet[List[ByteString]](Logging.simpleName(this) + ".out")
  override val shape = FlowShape(in, out)

  override protected def initialAttributes: Attributes = Attributes.name("CsvParsing")

  override def createLogic(inheritedAttributes: Attributes) =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private[this] val buffer = new CsvParser(delimiter, quoteChar, escapeChar, maximumLineLength)

      setHandlers(in, out, this)

      override def onPush(): Unit = {
        buffer.offer(grab(in))
        tryPollBuffer()
      }

      override def onPull(): Unit =
        tryPollBuffer()

      override def onUpstreamFinish(): Unit = {
        emitRemaining()
        completeStage()
      }

      private def tryPollBuffer() =
        try buffer.poll(requireLineEnd = true) match {
          case Some(csvLine) ⇒ push(out, csvLine)
          case _ ⇒
            if (isClosed(in)) {
              emitRemaining()
              completeStage()
            } else pull(in)
        } catch {
          case NonFatal(ex) ⇒ failStage(ex)
        }

      @tailrec private def emitRemaining(): Unit =
        buffer.poll(requireLineEnd = false) match {
          case Some(csvLine) ⇒
            emit(out, csvLine)
            emitRemaining()
          case _ ⇒
        }

    }
}
