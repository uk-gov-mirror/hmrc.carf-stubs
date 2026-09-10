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

package uk.gov.hmrc.carfstubs.helpers

import play.api.mvc.Result
import play.api.mvc.Results.{BadRequest, InternalServerError, NoContent}
import uk.gov.hmrc.carfstubs.models.request.Enrolment

trait EnrolmentHelper {
  def returnResponse(request: Enrolment): Result = {

    val badRequestMatches          = Set("8", "w")
    val internalServerErrorMatches = Set("9", "y")

    request match {
      case Enrolment(identifier :: _, _) if badRequestMatches.exists(identifier.value.toLowerCase.startsWith) =>
        badRequestResponse
      case Enrolment(identifier :: _, _)
          if internalServerErrorMatches.exists(identifier.value.toLowerCase.startsWith) =>
        internalServerErrorResponse
      case Enrolment(Nil, _)                                                                                  => badRequestResponse
      case Enrolment(identifier :: _, _)                                                                      => successfulResponse
    }
  }

  private def successfulResponse          = NoContent
  private def badRequestResponse          = BadRequest(
    "Provided service name is not in services-to-activate or No group ID in active auth session"
  )
  private def internalServerErrorResponse = InternalServerError("Internal Server Error")
}
