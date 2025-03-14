/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package example.auth.installed.commands

import org.apache.pekko.actor.typed
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import csw.aas.installed.api.InstalledAppAuthAdapter

// #write-command
class WriteCommand(val installedAppAuthAdapter: InstalledAppAuthAdapter, value: String)(implicit
    val actorSystem: typed.ActorSystem[?]
) extends AppCommand {
  override def run(): Unit = {

    installedAppAuthAdapter.getAccessToken() match {
      case Some(token) =>
        val bearerToken = headers.OAuth2BearerToken(token.value)
        val url         = s"http://localhost:7000/data?value=$value"
        Http()
          .singleRequest(
            HttpRequest(
              method = HttpMethods.POST,
              uri = Uri(url),
              headers = List(headers.Authorization(bearerToken))
            )
          )
          .map(response => {
            response.status match {
              case OK           => println("Success")
              case Unauthorized => println("Authentication failed")
              case Forbidden    => println("Permission denied")
              case code         => println(s"Unrecognised error: http status code = ${code.value}")
            }
          })(actorSystem.executionContext)

      case None =>
        println("you need to login before executing this command")
        System.exit(1)
    }
  }
}
// #write-command
