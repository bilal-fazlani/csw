/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package example.logging.client.genericlogger

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import csw.command.client.messages.ComponentMessage
import csw.logging.api.scaladsl.Logger
import csw.logging.client.scaladsl.GenericLoggerFactory

//#generic-logger-class
class GenericClass {

  val log: Logger = GenericLoggerFactory.getLogger
}
//#generic-logger-class

//#generic-logger-actor
object GenericActor {

  def behavior[T]: Behavior[T] =
    Behaviors.setup[T] { context =>
      val log: Logger = GenericLoggerFactory.getLogger(context)
      // actor setup

      Behaviors.receiveMessage { case _ => // handle messages and return new behavior
        Behaviors.same
      }
    }
}
//#generic-logger-actor

//#generic-logger-typed-actor
class GenericTypedActor(ctx: ActorContext[ComponentMessage]) {

  val log: Logger = GenericLoggerFactory.getLogger(ctx)
}
//#generic-logger-typed-actor
