/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package example.auth.installed.commands

import org.apache.pekko.actor.typed
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{HttpRequest, ResponseEntity, Uri}
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import example.auth.installed.commands.ReadCommand.convertToString

import scala.concurrent.duration.DurationLong
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

// #read-command
class ReadCommand(implicit val actorSystem: typed.ActorSystem[?]) extends AppCommand {
  implicit val ec: ExecutionContextExecutor = actorSystem.executionContext
  override def run(): Unit = {
    val url = "http://localhost:7000/data"
    Http()
      .singleRequest(HttpRequest(uri = Uri(url)))
      .map(response => {
        convertToString(response.entity).map(println)
      })
  }
}
// #read-command

object ReadCommand {
  def convertToString(entity: ResponseEntity)(implicit actorSystem: typed.ActorSystem[?]): Future[String] = {
    implicit val ec: ExecutionContext = actorSystem.executionContext
    Unmarshaller.stringUnmarshaller(entity)
  }
}
