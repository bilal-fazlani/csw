/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.location.server

import org.apache.pekko.Done
import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.http.scaladsl.Http.ServerBinding
import csw.location.server.cli.{ArgsParser, Options}
import csw.location.server.commons.ClusterAwareSettings
import csw.location.server.internal.ServerWiring

import scala.concurrent.Await
import scala.concurrent.duration.DurationDouble
import scala.util.control.NonFatal

/**
 * responsible for starting following:
 *  1. location service on provided port (this is required to bootstrap pekko cluster, initially cluster will have single seed node)
 *  2. http server which exposes http end point to change/get the log level of components dynamically
 */
// $COVERAGE-OFF$
object Main {
  private val name    = BuildInfo.name
  private val timeout = 30.seconds

  def main(args: Array[String]): Unit = start(args, startLogging = true)

  def start(args: Array[String], startLogging: Boolean = false): Option[(ServerBinding, ServerWiring)] =
    new ArgsParser(name).parse(args.toList).map { case options @ Options(_, _) =>
      requiredClusterSeedsSet()
      start(startLogging, options)
    }

  private[server] def start(startLogging: Boolean, options: Options): (ServerBinding, ServerWiring) = {
    val wiring = ServerWiring.make(options.clusterPort, options.outsideNetwork)

    import wiring._
    try {
      if (startLogging) actorRuntime.startLogging(name, clusterSettings.hostname)
      val locationBinding = Await.result(locationHttpService.start(options.httpBindHost), timeout)

      actorRuntime.coordinatedShutdown.addTask(
        CoordinatedShutdown.PhaseServiceUnbind,
        "unbind-services"
      )(() => locationBinding.terminate(30.seconds).map(_ => Done)(actorRuntime.ec))
      (locationBinding, wiring)
    }
    catch {
      case NonFatal(ex) =>
        ex.printStackTrace()
        Await.result(actorRuntime.shutdown(), timeout)
        throw ex
    }
  }

  private def requiredClusterSeedsSet(): Unit = {
    require(
      ClusterAwareSettings.seedNodes.nonEmpty,
      "[ERROR] CLUSTER_SEEDS setting is not specified either as env variable or system property. Please check online documentation for this set-up."
    )
  }
}
