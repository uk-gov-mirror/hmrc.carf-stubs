/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.carfstubs.models.submissionCallback

import play.api.libs.json.*

import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}

case class SdesCallback(
    notification: NotificationType,
    filename: String,
    checksumAlgorithm: String,
    checksum: String,
    correlationID: String,
    dateTime: Option[ZonedDateTime],
    failureReason: Option[String] = None
)

object SdesCallback {

  implicit val dateFormat: Format[ZonedDateTime] = new Format[ZonedDateTime] {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")

    override def reads(json: JsValue): JsResult[ZonedDateTime] =
      json.validate[String].map(ZonedDateTime.parse(_, formatter).withZoneSameInstant(ZoneOffset.UTC))

    override def writes(o: ZonedDateTime): JsValue = JsString(o.format(formatter))
  }

  implicit val format: OFormat[SdesCallback] = Json.format[SdesCallback]
}
