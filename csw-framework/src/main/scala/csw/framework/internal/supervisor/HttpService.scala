package csw.framework.internal.supervisor

import akka.actor
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import csw.location.api.scaladsl.{LocationService, RegistrationResult}
import csw.location.models
import csw.location.models.Connection.HttpConnection
import csw.logging.api.scaladsl.Logger

import scala.async.Async._
import scala.concurrent.Future

/**
 * Initialises a component at given port and register with location service
 *
 * @param locationService locationService instance to be used for registering this server with the location service
 * @param route instance representing the routes supported by this server
 */
class HttpService(
    locationService: LocationService,
    route: Route,
    log: Logger,
    httpConnection: HttpConnection
)(implicit actorSystem: ActorSystem[_]) {

  import actorSystem.executionContext

  lazy val registeredLazyBinding: Future[(ServerBinding, RegistrationResult)] = async {
    val binding            = await(bind())            // create HttpBinding with appropriate hostname and port
    val registrationResult = await(register(binding)) // create HttpRegistration and register it with location service

    // Add the task to unregister the HttpRegistration from location service.
    CoordinatedShutdown(actorSystem.toClassic).addTask(
      CoordinatedShutdown.PhaseBeforeServiceUnbind,
      s"unregistering-${registrationResult.location}"
    )(() => registrationResult.unregister())

    log.info(s"Server online at http://${binding.localAddress.getHostName}:${binding.localAddress.getPort}/")
    (binding, registrationResult)
  }

  private def bind() = {
    implicit val untypedActorSystem: actor.ActorSystem = actorSystem.toClassic
    implicit val mat: Materializer                     = Materializer(actorSystem)

    Http()(actorSystem.toClassic).bindAndHandle(
      handler = route,
      interface = "0.0.0.0",
      port = 0
    )
  }

  private def register(binding: ServerBinding): Future[RegistrationResult] = {
    val registration = models.HttpRegistration(
      connection = httpConnection,
      port = binding.localAddress.getPort,
      path = ""
    )
    log.info(
      s"Registering HTTP component with Location Service using registration: [${registration.toString}]"
    )
    locationService.register(registration)
  }
}