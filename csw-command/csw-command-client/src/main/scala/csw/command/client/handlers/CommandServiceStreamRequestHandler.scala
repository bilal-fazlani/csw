/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.command.client.handlers

import csw.command.api.codecs.CommandServiceCodecs.*
import csw.command.api.messages.CommandServiceStreamRequest
import csw.command.api.messages.CommandServiceStreamRequest.*
import csw.command.api.scaladsl.CommandService
import msocket.jvm.stream.{StreamRequestHandler, StreamResponse}

import scala.concurrent.Future

class CommandServiceStreamRequestHandler(commandService: CommandService)
    extends StreamRequestHandler[CommandServiceStreamRequest] {

  override def handle(request: CommandServiceStreamRequest): Future[StreamResponse] =
    request match {
      case QueryFinal(runId, timeout)   => response(commandService.queryFinal(runId)(timeout))
      case SubscribeCurrentState(names) => stream(commandService.subscribeCurrentState(names))
    }
}
