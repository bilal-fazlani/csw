package csw.framework.deploy.containercmd

import akka.Done
import akka.actor.typed.ActorRef
import com.typesafe.config.Config
import csw.framework.deploy.containercmd.cli.{ArgsParser, Options}
import csw.framework.exceptions.UnableToParseOptions
import csw.framework.internal.wiring.{Container, FrameworkWiring, Standalone}
import csw.location.client.utils.LocationServerStatus
import csw.logging.api.scaladsl.Logger
import csw.logging.client.scaladsl.LoggerFactory
import csw.prefix.models.{Prefix, Subsystem}

import java.io.Closeable
import java.nio.file.Path
import scala.async.Async.{async, await}
import scala.concurrent.duration.DurationDouble
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

// $COVERAGE-OFF$
object ContainerCmd {

  /**
   * Utility for starting a Container to host components or start a component in Standalone mode.
   *
   * @param name              The name to be used for the main app which uses this utility
   * @param args              The command line args accepted in the main app which uses this utility
   * @param subsystem         The subsystem which starts the container(used for logging)
   * @param defaultConfig     The default configuration which specifies the container or the component to be started
   * alone without any container
   * @return                  Actor ref of the container or supervisor of the component started without container
   */
  def start(name: String, subsystem: Subsystem, args: Array[String], defaultConfig: Option[Config] = None): Closeable = {
    val container = new ContainerCmd(name, subsystem, true, defaultConfig)
    container.start(args)
    () => container.shutdown()
  }
}

private[framework] class ContainerCmd(
    name: String,
    subsystem: Subsystem,
    startLogging: Boolean,
    defaultConfig: Option[Config] = None
) {
  private val log: Logger = new LoggerFactory(Prefix(subsystem, name)).getLogger

  private lazy val wiring: FrameworkWiring = new FrameworkWiring
  import wiring.actorRuntime.*

  def start(args: Array[String]): ActorRef[_] =
    new ArgsParser(name).parse(args.toList) match {
      case None => throw UnableToParseOptions
      case Some(Options(standalone, isLocal, inputFilePath)) =>
        LocationServerStatus.requireUpLocally()

        if (startLogging) wiring.actorRuntime.startLogging(name)

        log.debug(s"$name started with following arguments [${args.mkString(",")}]")

        try {
          val actorRef = Await.result(createF(standalone, isLocal, inputFilePath, defaultConfig), 30.seconds)
          log.info(s"Component is successfully created with actor actorRef $actorRef")
          actorRef
        }
        catch {
          case NonFatal(ex) =>
            log.error(s"${ex.getMessage}", ex = ex)
            shutdown()
            throw ex
        }
    }

  // fetch config file and start components in container mode or a single component in standalone mode
  private def createF(
      standalone: Boolean,
      isLocal: Boolean,
      inputFilePath: Option[Path],
      defaultConfig: Option[Config]
  ): Future[ActorRef[_]] =
    async {
      val config   = await(wiring.configUtils.getConfig(isLocal, inputFilePath, defaultConfig))
      val actorRef = await(createComponent(standalone, wiring, config))
      log.info(s"Component is successfully created with actor actorRef $actorRef")
      actorRef
    }

  private def createComponent(standalone: Boolean, wiring: FrameworkWiring, config: Config): Future[ActorRef[_]] =
    if (standalone) Standalone.spawn(config, wiring)
    else Container.spawn(config, wiring)

  private def shutdown(): Done = Await.result(wiring.actorRuntime.shutdown(), 10.seconds)
}
// $COVERAGE-ON$
