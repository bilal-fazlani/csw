/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package example.event

import org.apache.pekko.actor.Cancellable
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.apache.pekko.util.Timeout
import csw.event.api.scaladsl.EventPublisher
import csw.params.core.generics.{Key, KeyType}
import csw.params.events.{EventName, SystemEvent}
import csw.prefix.models.Prefix
import example.event.TemperatureMessage.*

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationDouble

class ConcurrencyInCallbacksExample(publisher: EventPublisher)(implicit actorSystem: ActorSystem[?]) {

  def behavior(): Behavior[TemperatureMessage] =
    Behaviors.setup { ctx =>
      implicit val timeout: Timeout     = Timeout(5.seconds)
      implicit val ec: ExecutionContext = actorSystem.executionContext

      // Mutable state which needs to be mutated from anywhere from the program
      var currentTemperature: Temperature = Temperature(0)

      var cancellable: Cancellable = null

      val prefix: Prefix                  = Prefix("wfos.blue.assembly")
      val temperatureKey: Key[Int]        = KeyType.IntKey.make("temperature")
      val temperatureEventName: EventName = EventName("temperature")

      def makeEvent(temp: Temperature) = Some(SystemEvent(prefix, temperatureEventName).add(temperatureKey.set(temp.degrees)))

      Behaviors.receiveMessage { msg =>
        msg match {
          case GetTemperature(ref) => ref ! currentTemperature
          case PublishTemperature  =>
            // Publishes the current Temperature every 50.millis

            // This is an INCORRECT way because mutable state is being accessed inside a callback which is not thread-safe.
            cancellable = publisher.publish(makeEvent(currentTemperature), 50.millis)

            // This is the CORRECT way to publish the mutable state by sending a message to the Actor in which the mutable state is kept (which in this case, is self)
            // The Async API of Publisher is called since the eventGenerator callback returns a Future[Event]
            cancellable = publisher.publishAsync((ctx.self ? GetTemperature.apply).map(makeEvent), 50.millis)

          case CancelPublishingTemperature => cancellable.cancel()
          case TemperatureRise(rise)       => currentTemperature = Temperature(currentTemperature.degrees + rise)
          case TemperatureDrop(drop)       => currentTemperature = Temperature(currentTemperature.degrees - drop)
        }
        Behaviors.same
      }
    }
}

case class Temperature(degrees: Int)
sealed trait TemperatureMessage
object TemperatureMessage {
  case class GetTemperature(ref: ActorRef[Temperature]) extends TemperatureMessage
  case object PublishTemperature                        extends TemperatureMessage
  case object CancelPublishingTemperature               extends TemperatureMessage
  case class TemperatureRise(degrees: Int)              extends TemperatureMessage
  case class TemperatureDrop(degrees: Int)              extends TemperatureMessage
}
