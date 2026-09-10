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

package controllers

import base.SpecBase
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalacheck.Gen
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.api.{inject, Application}
import uk.gov.hmrc.carfstubs.config.AppConfig
import uk.gov.hmrc.carfstubs.connectors.{BusinessRulesCallbackConnector, SdesCallbackConnector}
import uk.gov.hmrc.carfstubs.controllers.routes
import uk.gov.hmrc.carfstubs.models.errors.{InternalServerError, XmlValidationError}
import uk.gov.hmrc.carfstubs.models.submissionCallback.NotificationType
import uk.gov.hmrc.carfstubs.models.submissionCallback.NotificationType.*
import uk.gov.hmrc.carfstubs.types.ResultT

class SdesCallbackControllerSpec extends SpecBase {

  val mockSdesCallbackConnector: SdesCallbackConnector                   = mock[SdesCallbackConnector]
  val mockBusinessRulesCallbackConnector: BusinessRulesCallbackConnector = mock[BusinessRulesCallbackConnector]
  val mockAppConfig: AppConfig                                           = mock[AppConfig]

  private def application(): Application =
    new GuiceApplicationBuilder()
      .overrides(
        bind[SdesCallbackConnector].toInstance(mockSdesCallbackConnector),
        bind[BusinessRulesCallbackConnector].toInstance(mockBusinessRulesCallbackConnector),
        bind[AppConfig].toInstance(mockAppConfig)
      )
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSdesCallbackConnector, mockBusinessRulesCallbackConnector, mockAppConfig)
  }

  "SdesCallbackController" - {
    ".callback" - {
      "must return BadRequest when the request body is invalid" in {
        val json: JsValue = Json.parse("""{"field": "invalid"}""")
        val request       = FakeRequest(POST, routes.SdesCallbackController.callback.url).withBody(json)
        val result        = route(application(), request).value

        status(result) mustBe BAD_REQUEST

        verify(mockSdesCallbackConnector, times(0)).callback(any())(any())
        verify(mockBusinessRulesCallbackConnector, times(0)).callback(any(), any())(any())
      }

      "must call SdesCallbackConnector and not BusinessRulesCallbackConnector when notification is not FileProcessed" - {
        val notificationTypeGen: Gen[NotificationType] = oneOf(Seq(FileReady, FileReceived, FileProcessingFailure))

        "when SdesCallbackConnector returns a success response" in {
          when(mockSdesCallbackConnector.callback(any())(any())).thenReturn(ResultT.fromValue(()))

          val notificationType = notificationTypeGen.sample.get
          val filename         = "filename.xml"
          val json: JsValue    = buildSdesCallbackJson(notificationType, filename, None)
          val request          = FakeRequest(POST, routes.SdesCallbackController.callback.url).withBody(json)
          val result           = route(application(), request).value

          status(result) mustBe OK

          verify(mockSdesCallbackConnector, times(1)).callback(
            eqTo(sdesCallback(filename).copy(notification = notificationType))
          )(any())
          verify(mockBusinessRulesCallbackConnector, times(0)).callback(any(), any())(any())
        }

        "when SdesCallbackConnector returns an error" in {
          when(mockSdesCallbackConnector.callback(any())(any())).thenReturn(ResultT.fromError(InternalServerError))

          val notificationType = notificationTypeGen.sample.get
          val filename         = "filename.xml"
          val json: JsValue    = buildSdesCallbackJson(notificationType, filename, None)
          val request          = FakeRequest(POST, routes.SdesCallbackController.callback.url).withBody(json)
          val result           = route(application(), request).value

          status(result) mustBe INTERNAL_SERVER_ERROR

          verify(mockSdesCallbackConnector, times(1)).callback(
            eqTo(sdesCallback(filename).copy(notification = notificationType))
          )(any())
          verify(mockBusinessRulesCallbackConnector, times(0)).callback(any(), any())(any())
        }
      }

      "must call SdesCallbackConnector with FileProcessingFailure when notification is FileProcessed but file name contains 'virus' or 'unexpected'" - {
        "when file name contains 'virus'" in {
          when(mockSdesCallbackConnector.callback(any())(any())).thenReturn(ResultT.fromValue(()))

          val filename      = "virus-file.xml"
          val json: JsValue = buildSdesCallbackJson(FileProcessed, filename, None)
          val request       = FakeRequest(POST, routes.SdesCallbackController.callback.url).withBody(json)
          val result        = route(application(), request).value

          status(result) mustBe OK

          verify(mockSdesCallbackConnector, times(1)).callback(
            eqTo(sdesCallback(filename).copy(notification = FileProcessingFailure, failureReason = Some("virus")))
          )(any())
          verify(mockBusinessRulesCallbackConnector, times(0)).callback(any(), any())(any())
        }

        "when file name contains 'unexpected'" in {
          when(mockSdesCallbackConnector.callback(any())(any())).thenReturn(ResultT.fromValue(()))

          val filename      = "unexpected-error.xml"
          val json: JsValue = buildSdesCallbackJson(FileProcessed, filename, None)
          val request       = FakeRequest(POST, routes.SdesCallbackController.callback.url).withBody(json)
          val result        = route(application(), request).value

          status(result) mustBe OK

          verify(mockSdesCallbackConnector, times(1)).callback(
            eqTo(sdesCallback(filename).copy(notification = FileProcessingFailure))
          )(any())
          verify(mockBusinessRulesCallbackConnector, times(0)).callback(any(), any())(any())
        }
      }

      "must call SdesCallbackConnector and BusinessRulesCallbackConnector when notification is FileProcessed" - {
        when(mockAppConfig.fastCallbackTimeInSeconds).thenReturn(0)
        when(mockAppConfig.slowCallbackTimeInSeconds).thenReturn(1)

        "when file name contains 'accepted'" in {
          when(mockSdesCallbackConnector.callback(any())(any())).thenReturn(ResultT.fromValue(()))
          when(mockBusinessRulesCallbackConnector.callback(any(), any())(any())).thenReturn(ResultT.fromValue(()))

          val filename      = "accepted-file.xml"
          val json: JsValue = buildSdesCallbackJson(FileProcessed, filename, None)
          val request       = FakeRequest(POST, routes.SdesCallbackController.callback.url).withBody(json)
          val result        = route(application(), request).value

          status(result) mustBe OK

          verify(mockSdesCallbackConnector, times(1)).callback(eqTo(sdesCallback(filename)))(any())
          verify(mockBusinessRulesCallbackConnector, times(1)).callback(
            eqTo(testConversationId),
            eqTo("data/examples/aeoi/BusinessRuleCheckSampleRequest_ValidFile_v0.3.xml")
          )(any())
          verify(mockAppConfig, times(1)).fastCallbackTimeInSeconds
        }

        "when file name contains 'rejected'" - {
          "and file name contains 'many'" in {
            when(mockSdesCallbackConnector.callback(any())(any())).thenReturn(ResultT.fromValue(()))
            when(mockBusinessRulesCallbackConnector.callback(any(), any())(any())).thenReturn(ResultT.fromValue(()))

            val filename      = "rejected-many-errors.xml"
            val json: JsValue = buildSdesCallbackJson(FileProcessed, filename, None)
            val request       = FakeRequest(POST, routes.SdesCallbackController.callback.url).withBody(json)
            val result        = route(application(), request).value

            status(result) mustBe OK

            verify(mockSdesCallbackConnector, times(1)).callback(eqTo(sdesCallback(filename)))(any())
            verify(mockBusinessRulesCallbackConnector, times(1)).callback(
              eqTo(testConversationId),
              eqTo("data/examples/aeoi/BusinessRuleCheckSampleRequest_validFile_with_150_errors.xml")
            )(any())
            verify(mockAppConfig, times(1)).fastCallbackTimeInSeconds
          }

          "and file name does not contain 'many'" in {
            when(mockSdesCallbackConnector.callback(any())(any())).thenReturn(ResultT.fromValue(()))
            when(mockBusinessRulesCallbackConnector.callback(any(), any())(any())).thenReturn(ResultT.fromValue(()))

            val filename      = "rejected-few-errors.xml"
            val json: JsValue = buildSdesCallbackJson(FileProcessed, filename, None)
            val request       = FakeRequest(POST, routes.SdesCallbackController.callback.url).withBody(json)
            val result        = route(application(), request).value

            status(result) mustBe OK

            verify(mockSdesCallbackConnector, times(1)).callback(eqTo(sdesCallback(filename)))(any())
            verify(mockBusinessRulesCallbackConnector, times(1)).callback(
              eqTo(testConversationId),
              eqTo("data/examples/aeoi/BusinessRuleCheckSampleRequest_validFile_with_errors.xml")
            )(any())
            verify(mockAppConfig, times(1)).fastCallbackTimeInSeconds
          }
        }

        "when file name contains 'schema-error'" in {
          when(mockSdesCallbackConnector.callback(any())(any())).thenReturn(ResultT.fromValue(()))
          when(mockBusinessRulesCallbackConnector.callback(any(), any())(any())).thenReturn(ResultT.fromValue(()))

          val filename      = "schema-errors.xml"
          val json: JsValue = buildSdesCallbackJson(FileProcessed, filename, None)
          val request       = FakeRequest(POST, routes.SdesCallbackController.callback.url).withBody(json)
          val result        = route(application(), request).value

          status(result) mustBe OK

          verify(mockSdesCallbackConnector, times(1)).callback(eqTo(sdesCallback(filename)))(any())
          verify(mockBusinessRulesCallbackConnector, times(1)).callback(
            eqTo(testConversationId),
            eqTo("data/examples/aeoi/BusinessRuleCheckSampleRequest_invalidFile_schema__errors.xml")
          )(any())
          verify(mockAppConfig, times(1)).fastCallbackTimeInSeconds
        }

        "when file name contains 'malformed'" in {
          when(mockSdesCallbackConnector.callback(any())(any())).thenReturn(ResultT.fromValue(()))
          when(mockBusinessRulesCallbackConnector.callback(any(), any())(any())).thenReturn(ResultT.fromValue(()))

          val filename      = "malformed-xml.xml"
          val json: JsValue = buildSdesCallbackJson(FileProcessed, filename, None)
          val request       = FakeRequest(POST, routes.SdesCallbackController.callback.url).withBody(json)
          val result        = route(application(), request).value

          status(result) mustBe OK

          verify(mockSdesCallbackConnector, times(1)).callback(eqTo(sdesCallback(filename)))(any())
          verify(mockBusinessRulesCallbackConnector, times(1)).callback(
            eqTo(testConversationId),
            eqTo("data/examples/malformed-xml.xml")
          )(any())
          verify(mockAppConfig, times(1)).fastCallbackTimeInSeconds
        }

        "when file name contains 'not-found'" in {
          when(mockSdesCallbackConnector.callback(any())(any())).thenReturn(ResultT.fromValue(()))
          when(mockBusinessRulesCallbackConnector.callback(any(), any())(any())).thenReturn(ResultT.fromValue(()))

          val filename      = "file-not-found.xml"
          val json: JsValue = buildSdesCallbackJson(FileProcessed, filename, None)
          val request       = FakeRequest(POST, routes.SdesCallbackController.callback.url).withBody(json)
          val result        = route(application(), request).value

          status(result) mustBe OK

          verify(mockSdesCallbackConnector, times(1)).callback(eqTo(sdesCallback(filename)))(any())
          verify(mockBusinessRulesCallbackConnector, times(1)).callback(
            eqTo(testConversationId),
            eqTo("data/examples/aeoi/unknown.xml")
          )(any())
          verify(mockAppConfig, times(1)).fastCallbackTimeInSeconds
        }

        "when file name contains none of the above, does not send the 2nd callback (file remains Pending)" in {
          when(mockSdesCallbackConnector.callback(any())(any())).thenReturn(ResultT.fromValue(()))
          when(mockBusinessRulesCallbackConnector.callback(any(), any())(any())).thenReturn(ResultT.fromValue(()))

          val filename      = "other.xml"
          val json: JsValue = buildSdesCallbackJson(FileProcessed, filename, None)
          val request       = FakeRequest(POST, routes.SdesCallbackController.callback.url).withBody(json)
          val result        = route(application(), request).value

          status(result) mustBe OK

          verify(mockSdesCallbackConnector, times(1)).callback(eqTo(sdesCallback(filename)))(any())
          verify(mockBusinessRulesCallbackConnector, times(0)).callback(any(), any())(any())
        }

        "must use the slow callback time when file name contains 'slow'" in {
          when(mockSdesCallbackConnector.callback(any())(any())).thenReturn(ResultT.fromValue(()))
          when(mockBusinessRulesCallbackConnector.callback(any(), any())(any())).thenReturn(ResultT.fromValue(()))

          val filename      = "accepted-file-slow.xml"
          val json: JsValue = buildSdesCallbackJson(FileProcessed, filename, None)
          val request       = FakeRequest(POST, routes.SdesCallbackController.callback.url).withBody(json)
          val result        = route(application(), request).value

          status(result) mustBe OK

          verify(mockSdesCallbackConnector, times(1)).callback(eqTo(sdesCallback(filename)))(any())
          verify(mockBusinessRulesCallbackConnector, times(1)).callback(
            eqTo(testConversationId),
            eqTo("data/examples/aeoi/BusinessRuleCheckSampleRequest_ValidFile_v0.3.xml")
          )(any())
          verify(mockAppConfig, times(1)).slowCallbackTimeInSeconds
        }

        "must return OK even when the 2nd callback returns an error (e.g. due to malformed XML)" in {
          when(mockSdesCallbackConnector.callback(any())(any())).thenReturn(ResultT.fromValue(()))
          when(mockBusinessRulesCallbackConnector.callback(any(), any())(any()))
            .thenReturn(ResultT.fromError(XmlValidationError))

          val filename      = "malformed-xml.xml"
          val json: JsValue = buildSdesCallbackJson(FileProcessed, filename, None)
          val request       = FakeRequest(POST, routes.SdesCallbackController.callback.url).withBody(json)
          val result        = route(application(), request).value

          status(result) mustBe OK

          verify(mockSdesCallbackConnector, times(1)).callback(eqTo(sdesCallback(filename)))(any())
          verify(mockBusinessRulesCallbackConnector, times(1)).callback(
            eqTo(testConversationId),
            eqTo("data/examples/malformed-xml.xml")
          )(any())
          verify(mockAppConfig, times(1)).fastCallbackTimeInSeconds
        }
      }
    }
  }

  private def buildSdesCallbackJson(
      notification: NotificationType,
      filename: String,
      failureReason: Option[String]
  ): JsValue =
    Json.parse(
      s"""
         |{
         | "notification": "$notification",
         | "filename": "$filename",
         | "checksumAlgorithm": "$checksumAlgorithm",
         | "checksum": "$testChecksum",
         | "correlationID": "$testConversationId",
         | "dateTime": "2024-06-11T15:07:47.838Z"
         | ${failureReason.fold("")(reason => s""", "failureReason": "$reason""")}
         |}
         |""".stripMargin
    )
}
