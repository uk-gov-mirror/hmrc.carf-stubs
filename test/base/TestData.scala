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

package base

import org.scalacheck.Gen
import org.scalacheck.Gen.choose
import uk.gov.hmrc.carfstubs.models.submissionCallback.NotificationType.FileProcessed
import uk.gov.hmrc.carfstubs.models.submissionCallback.SdesCallback

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.UUID

trait TestData {

  val clock: Clock = Clock.fixed(Instant.ofEpochMilli(1718118467838L), ZoneId.of("UTC"))

  val uuid: String = UUID.randomUUID().toString

  val testConversationId: String = uuid
  val testChecksum: String       = "396f1"
  val checksumAlgorithm: String  = "SHA-256"
  val testDownloadUrl: String    = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676"

  def sdesCallback(filename: String) = SdesCallback(
    notification = FileProcessed,
    filename = filename,
    checksumAlgorithm = checksumAlgorithm,
    checksum = testChecksum,
    correlationID = testConversationId,
    dateTime = Some(Instant.now(clock).atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneOffset.UTC)),
    failureReason = None
  )

  def oneOf[T](xs: Seq[Gen[T]]): Gen[T] =
    if (xs.isEmpty) {
      throw new IllegalArgumentException("oneOf called on empty collection")
    } else {
      val vector = xs.toVector
      choose(0, vector.size - 1).flatMap(vector(_))
    }

}
