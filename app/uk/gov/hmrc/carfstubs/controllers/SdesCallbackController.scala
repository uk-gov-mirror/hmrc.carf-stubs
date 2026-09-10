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

package uk.gov.hmrc.carfstubs.controllers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern
import play.api.libs.json.JsValue
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.carfstubs.config.AppConfig
import uk.gov.hmrc.carfstubs.connectors.{BusinessRulesCallbackConnector, SdesCallbackConnector}
import uk.gov.hmrc.carfstubs.models.submissionCallback.NotificationType.{FileProcessed, FileProcessingFailure}
import uk.gov.hmrc.carfstubs.models.submissionCallback.SdesCallback
import uk.gov.hmrc.carfstubs.types.ResultT
import uk.gov.hmrc.carfstubs.utils.LoggerUtil.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SdesCallbackController @Inject() (
    cc: ControllerComponents,
    sdesCallbackConnector: SdesCallbackConnector,
    businessRulesCallbackConnector: BusinessRulesCallbackConnector,
    system: ActorSystem,
    appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def callback: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body
      .validate[SdesCallback]
      .fold(
        invalid = _ => Future.successful(BadRequest("Could not parse request body as SdesCallback")),
        valid = sdesCallback =>
          logInfo(
            s"[SdesCallbackController][callback] Received SDES callback. Notification type ${sdesCallback.notification}, file: ${sdesCallback.filename}, conversationId: ${sdesCallback.correlationID}"
          )
          val sdesCallbackRequest = updateStatusOfSdesCallback(sdesCallback)
          sdesCallbackConnector.callback(sdesCallbackRequest).value.map {
            case Left(error) =>
              logWarn(s"[SdesCallbackController][callback] Error from SDES callback: $error")
              InternalServerError("Error from SDES callback")
            case Right(_)    =>
              sdesCallbackRequest.notification match {
                case FileProcessed =>
                  sendBusinessRulesCallback(sdesCallback.correlationID, sdesCallback.filename.toLowerCase)
                  Ok
                case _             => Ok
              }
          }
      )
  }

  private def updateStatusOfSdesCallback(sdesCallback: SdesCallback): SdesCallback = {
    val containsVirus = sdesCallback.filename.toLowerCase.contains("virus")
    if (
      sdesCallback.notification == FileProcessed &&
      (containsVirus || sdesCallback.filename.toLowerCase.contains("unexpected"))
    ) {
      sdesCallback.copy(
        notification = FileProcessingFailure,
        failureReason = if containsVirus then Some("virus") else None
      )
    } else {
      sdesCallback
    }
  }

  private def sendBusinessRulesCallback(
      conversationId: String,
      filename: String
  )(implicit hc: HeaderCarrier): ResultT[Unit] = {
    val delayTime: FiniteDuration =
      if (filename.contains("slow")) { appConfig.slowCallbackTimeInSeconds.seconds }
      else { appConfig.fastCallbackTimeInSeconds.seconds }

    if (filename.contains("accepted")) {
      businessRulesCallbackWithDelay(
        conversationId,
        delayTime,
        "data/examples/aeoi/BusinessRuleCheckSampleRequest_ValidFile_v0.3.xml"
      )
    } else if (filename.contains("rejected")) {
      if (filename.contains("many")) {
        businessRulesCallbackWithDelay(
          conversationId,
          delayTime,
          "data/examples/aeoi/BusinessRuleCheckSampleRequest_validFile_with_150_errors.xml"
        )
      } else {
        businessRulesCallbackWithDelay(
          conversationId,
          delayTime,
          "data/examples/aeoi/BusinessRuleCheckSampleRequest_validFile_with_errors.xml"
        )
      }
    } else if (filename.contains("schema-error")) {
      businessRulesCallbackWithDelay(
        conversationId,
        delayTime,
        "data/examples/aeoi/BusinessRuleCheckSampleRequest_invalidFile_schema__errors.xml"
      )
    } else if (filename.contains("malformed")) {
      businessRulesCallbackWithDelay(
        conversationId,
        delayTime,
        "data/examples/malformed-xml.xml"
      )
    } else if (filename.contains("not-found")) {
      businessRulesCallbackWithDelay(
        conversationId,
        delayTime,
        "data/examples/aeoi/unknown.xml"
      )
    } else {
      ResultT.fromValue(())
    }
  }

  private def businessRulesCallbackWithDelay(
      conversationId: String,
      delay: FiniteDuration,
      downloadUrl: String
  )(implicit hc: HeaderCarrier): ResultT[Unit] =
    ResultT.fromFuture {
      pattern.after(delay, system.scheduler) {
        businessRulesCallbackConnector.callback(conversationId, downloadUrl).value
      }
    }

}
