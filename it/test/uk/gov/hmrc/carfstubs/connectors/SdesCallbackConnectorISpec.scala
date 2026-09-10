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

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalactic.Prettifier.default
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import play.api.http.Status.*
import play.api.libs.json.Json
import uk.gov.hmrc.carfstubs.itutil.ApplicationWithWiremock
import uk.gov.hmrc.carfstubs.models.errors.InternalServerError

class SdesCallbackConnectorISpec
    extends ApplicationWithWiremock
    with Matchers
    with ScalaFutures
    with IntegrationPatience {

  lazy val connector: SdesCallbackConnector = app.injector.instanceOf[SdesCallbackConnector]

  ".callback" - {

    val testUrl = "/carf-reporting/fts/callback"

    "must return Unit given a 200 response" in {
      stubFor(
        post(urlPathMatching(testUrl))
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )

      val result = connector.callback(sdesCallback("filename.xml")).value.futureValue
      result mustBe Right(())
    }

    "must return InternalServerError given a 400 response" in {
      stubFor(
        get(urlPathMatching(baseUrl))
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
              .withBody(Json.obj("message" -> "Bad request").toString)
          )
      )

      val result = connector.callback(sdesCallback("filename.xml")).value.futureValue
      result mustBe Left(InternalServerError)
    }

    "must return InternalServerError given a 500 response" in {
      stubFor(
        get(urlPathMatching(baseUrl))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
              .withBody(Json.obj("message" -> "Internal server error").toString)
          )
      )

      val result = connector.callback(sdesCallback("filename.xml")).value.futureValue
      result mustBe Left(InternalServerError)
    }
  }
}
