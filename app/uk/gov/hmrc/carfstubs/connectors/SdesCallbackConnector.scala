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

import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.carfstubs.config.AppConfig
import uk.gov.hmrc.carfstubs.models.errors.InternalServerError
import uk.gov.hmrc.carfstubs.models.submissionCallback.SdesCallback
import uk.gov.hmrc.carfstubs.types.ResultT
import uk.gov.hmrc.carfstubs.utils.LoggerUtil.*
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class SdesCallbackConnector @Inject() (httpClient: HttpClientV2, config: AppConfig)(implicit ec: ExecutionContext) {

  def callback(sdesCallback: SdesCallback)(implicit hc: HeaderCarrier): ResultT[Unit] = {
    val callBackUrl = url"${config.carfReportingBaseUrl}/carf-reporting/fts/callback"

    ResultT.fromFuture {
      httpClient
        .post(callBackUrl)
        .withBody(Json.toJson(sdesCallback))
        .execute[HttpResponse]
        .map { httpResponse =>
          httpResponse.status match {
            case OK =>
              logInfo(s"[SdesCallbackConnector][callback] Successful call to endpoint: ${callBackUrl.toURI}")
              Right(())
            case _  =>
              logWarn(
                s"[SdesCallbackConnector][callback] Unexpected response. Status code: ${httpResponse.status}, from endpoint: ${callBackUrl.toURI}"
              )
              Left(InternalServerError)
          }
        }
    }
  }

}
