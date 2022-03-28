/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.logging.client.scaladsl

import java.net.InetAddress

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import csw.logging.client.appenders.{LogAppenderBuilder, StdOutAppender}
import csw.logging.client.internal.LoggingSystem
import play.api.libs.json.JsObject

private[csw] class StdOutTestAppender(system: ActorSystem[_], stdHeaders: JsObject, logPrinter: Any => Unit)
    extends StdOutAppender(system, stdHeaders, logPrinter) {
  override val color   = true
  override val oneLine = true
}

private[csw] object StdOutTestAppender extends LogAppenderBuilder {
  def apply(system: ActorSystem[_], stdHeaders: JsObject): StdOutTestAppender =
    new StdOutTestAppender(system, stdHeaders, println)
}

object LoggingSystemFactory {

  /**
   * The factory used to create the LoggingSystem. `LoggingSystem` should be started once in an app.
   *
   * @note it is recommended to use this method for only testing
   * @return the instance of LoggingSystem
   */
  private[logging] def start(): LoggingSystem =
    new LoggingSystem(
      "foo-name",
      "foo-version",
      InetAddress.getLocalHost.getHostName,
      ActorSystem(SpawnProtocol(), "logging")
    )

  /**
   * The factory used to create the LoggingSystem. `LoggingSystem` should be started once in an app.
   *
   * @param name The name of the logging system. If there is a file appender configured, then a file with this name is
   *             created on local machine.
   * @param version the version of the csw which will be a part of log statements
   * @param hostName the host address which will be a part of log statements
   * @param actorSystem the ActorSystem used to create LogActor from LoggingSystem
   * @return the instance of LoggingSystem
   */
  def start(name: String, version: String, hostName: String, actorSystem: ActorSystem[SpawnProtocol.Command]): LoggingSystem =
    new LoggingSystem(name, version, hostName, actorSystem)

  def forTestingOnly()(implicit actorSystem: ActorSystem[SpawnProtocol.Command]): LoggingSystem = {
    val loggingSystem = new LoggingSystem("test-name", "test-version-1", "localhost", actorSystem)
    loggingSystem.addAppenders(StdOutTestAppender)
    loggingSystem
  }
}
