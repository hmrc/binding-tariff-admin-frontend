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

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString

object CsvParsing {

  val Backslash: Byte = '\\'
  val Comma: Byte = ','
  val SemiColon: Byte = ';'
  val Colon: Byte = ':'
  val Tab: Byte = '\t'
  val DoubleQuote: Byte = '"'
  val maximumLineLengthDefault: Int = 10 * 1024

  /** Creates CSV parsing flow that reads CSV lines from incoming
    * [[akka.util.ByteString]] objects.
    */
  def lineScanner(delimiter: Byte = Comma,
                  quoteChar: Byte = DoubleQuote,
                  escapeChar: Byte = Backslash,
                  maximumLineLength: Int = maximumLineLengthDefault): Flow[ByteString, List[ByteString], NotUsed] =
    Flow.fromGraph(new CsvParsingStage(delimiter, quoteChar, escapeChar, maximumLineLength))
}
