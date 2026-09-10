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

package uk.gov.hmrc.carfstubs.connectors

import play.api.http.HeaderNames
import play.api.http.Status.{OK, UNPROCESSABLE_ENTITY}
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.carfstubs.config.AppConfig
import uk.gov.hmrc.carfstubs.models.errors.{InternalServerError, XmlValidationError}
import uk.gov.hmrc.carfstubs.models.submissionCallback.BusinessRulesValidationRequest
import uk.gov.hmrc.carfstubs.types.ResultT
import uk.gov.hmrc.carfstubs.utils.LoggerUtil.*
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class BusinessRulesCallbackConnector @Inject() (httpClient: HttpClientV2, config: AppConfig)(implicit
    ec: ExecutionContext
) {

  def callback(conversationId: String, downloadUrl: String)(implicit hc: HeaderCarrier): ResultT[Unit] = {
    val businessRulesCallbackUrl = url"${config.carfReportingBaseUrl}/carf-reporting/validate-extract-business-rules"

    ResultT.fromFuture {
      httpClient
        .post(businessRulesCallbackUrl)
        .withBody(Json.toJson(BusinessRulesValidationRequest(downloadUrl)))
        .setHeader(headers(conversationId): _*)
        .execute[HttpResponse]
        .map { httpResponse =>
          httpResponse.status match {
            case OK                   =>
              logInfo(
                s"[BusinessRulesCallbackConnector][callback] Successful call to endpoint: ${businessRulesCallbackUrl.toURI}"
              )
              Right(())
            case UNPROCESSABLE_ENTITY =>
              logWarn(
                s"[SdesCallbackConnector][callback] Failed to process XML file with call to endpoint: ${businessRulesCallbackUrl.toURI}"
              )
              Left(XmlValidationError)
            case _                    =>
              logWarn(
                s"Unexpected response. Status code: ${httpResponse.status}, from endpoint: ${businessRulesCallbackUrl.toURI}"
              )
              Left(InternalServerError)
          }
        }
    }
  }

  private def headers(conversationId: String): Seq[(String, String)] = Seq(
    HeaderNames.CONTENT_TYPE  -> "application/json",
    "x-conversation-id"       -> conversationId,
    HeaderNames.AUTHORIZATION -> s"Bearer ${config.bearerToken("br-response")}"
  )

}
