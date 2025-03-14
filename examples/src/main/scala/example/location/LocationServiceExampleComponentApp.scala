/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package example.location

import java.net.InetAddress
import org.apache.pekko.actor.typed
import org.apache.pekko.actor.typed.SpawnProtocol
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import csw.location.api.PekkoRegistrationFactory
import csw.location.api.models.Connection.PekkoConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.location.api.scaladsl.{LocationService, RegistrationResult}
import csw.location.client.ActorSystemFactory
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.api.scaladsl.Logger
import csw.logging.client.commons.PekkoTypedExtension.UserActorFactory
import csw.logging.client.scaladsl.{LoggerFactory, LoggingSystemFactory}
import csw.prefix.models.{Prefix, Subsystem}
import example.location.LocationServiceExampleComponentApp.system

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

/**
 * An example that shows how to register a component actor with the location service.
 */
object LocationServiceExampleComponentApp {
  implicit val system: typed.ActorSystem[SpawnProtocol.Command] = ActorSystemFactory.remote(SpawnProtocol(), "example-system")
  implicit val ec: ExecutionContextExecutor                     = system.executionContext
  private val locationService                                   = HttpLocationServiceFactory.makeLocalClient

  // #create-logging-system
  private val host = InetAddress.getLocalHost.getHostName
  LoggingSystemFactory.start("LocationServiceExampleComponent", "0.1", host, system)
  // #create-logging-system

  def main(args: Array[String]): Unit = {
    system.spawn(LocationServiceExampleComponent.behaviour(locationService), LocationServiceExampleComponent.connection.name)
  }
}

object LocationServiceExampleComponent {
  // Component ID of the service
  val componentId = ComponentId(Prefix(Subsystem.CSW, "LocationServiceExampleComponent"), ComponentType.Assembly)

  // Connection for the service
  val connection = PekkoConnection(componentId)

  // Message sent from client once location has been resolved
  case class ClientMessage(replyTo: typed.ActorRef[?])

  def behaviour(locationService: LocationService)(implicit ec: ExecutionContextExecutor): Behaviors.Receive[ClientMessage] =
    Behaviors.receive[ClientMessage]((ctx, msg) => {
      val log: Logger = new LoggerFactory(Prefix("csw.my-component-name")).getLogger(ctx)
      log.info("In actor LocationServiceExampleComponent")
      // Register with the location service
      val registrationResult: Future[RegistrationResult] =
        locationService.register(
          PekkoRegistrationFactory.make(
            LocationServiceExampleComponent.connection,
            ctx.self
          )
        )

      registrationResult.map(_ => {
        log.info("LocationServiceExampleComponent registered.")
      })
      msg match {
        case ClientMessage(replyTo) =>
          log.info(s"Received scala client message from: $replyTo")
          Behaviors.same
      }
    })
}
