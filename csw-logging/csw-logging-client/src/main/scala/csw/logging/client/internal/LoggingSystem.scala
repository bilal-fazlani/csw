/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.logging.client.internal

import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.{ActorSystem, MailboxSelector, SpawnProtocol}
import ch.qos.logback.classic.LoggerContext
import csw.logging.client.appenders.LogAppenderBuilder
import csw.logging.client.commons.PekkoTypedExtension.UserActorFactory
import csw.logging.client.commons.LoggingKeys
import csw.logging.client.exceptions.AppenderNotFoundException
import csw.logging.client.internal.LogActorMessages.*
import csw.logging.client.internal.TimeActorMessages.TimeDone
import csw.logging.client.models.ComponentLoggingState
import csw.logging.models.{Level, Levels, LogMetadata}
import csw.prefix.models.Prefix
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, Json}

import scala.jdk.FutureConverters.*
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters.*

/**
 * This class is responsible for programmatic interaction with the configuration of the logging system. It initializes
 * appenders, starts the log actor and manages clean up of logging system. Until and unless this class is instantiated
 * all(pekko, slf4j and tmt) the logs are enqueued in local queue. Once it is instantiated, the queue is emptied and all
 * the logs are forwarded to configured appenders.
 *
 * @param name    name of the service (to log).
 * @param version version of the service (to log).
 * @param host    host name (to log).
 * @param system  an ActorSystem used to create log actors
 */
class LoggingSystem private[csw] (name: String, version: String, host: String, val system: ActorSystem[SpawnProtocol.Command]) {

  private val loggingConfig = system.settings.config.getConfig("csw-logging")

  private val defaultAppenderBuilders: List[LogAppenderBuilder] =
    loggingConfig.getStringList("appenders").asScala.toList.map(getAppenderInstance)

  @volatile var appenderBuilders: List[LogAppenderBuilder] = defaultAppenderBuilders

  private val levels = loggingConfig.getString("logLevel")
  private val defaultLevel: Level =
    if (Level.hasLevel(levels)) Level(levels)
    else throw new Exception(s"Bad value $levels for csw-logging.logLevel")

  LoggingState.logLevel = defaultLevel

  private val pekkoLogLevelS = loggingConfig.getString("pekkoLogLevel")
  private val defaultPekkoLogLevel: Level =
    if (Level.hasLevel(pekkoLogLevelS)) Level(pekkoLogLevelS)
    else throw new Exception(s"Bad value $pekkoLogLevelS for csw-logging.pekkoLogLevel")

  LoggingState.pekkoLogLevel = defaultPekkoLogLevel

  private val slf4jLogLevelS = loggingConfig.getString("slf4jLogLevel")
  private val defaultSlf4jLogLevel: Level =
    if (Level.hasLevel(slf4jLogLevelS)) Level(slf4jLogLevelS)
    else throw new Exception(s"Bad value $slf4jLogLevelS for csw-logging.slf4jLogLevel")

  LoggingState.slf4jLogLevel = defaultSlf4jLogLevel

  private val gc   = loggingConfig.getBoolean("gc")
  private val time = loggingConfig.getBoolean("time")

  private implicit val ec: ExecutionContext = system.executionContext
  private val done                          = Promise[Unit]()
  private val timeActorDonePromise          = Promise[Unit]()

  private val initialComponentsLoggingState = ComponentLoggingStateManager.from(loggingConfig)

  LoggingState.componentsLoggingState.putAll(initialComponentsLoggingState)

  /**
   * Standard headers.
   */
  val standardHeaders: JsObject = Json.obj(LoggingKeys.HOST -> host, LoggingKeys.NAME -> name, LoggingKeys.VERSION -> version)

  setDefaultLogLevel(defaultLevel)
  LoggingState.loggerStopping = false
  LoggingState.doTime = false
  LoggingState.timeActorOption = None

  @volatile private var appenders = appenderBuilders.map {
    _.apply(system, standardHeaders)
  }

  private val logActor = system.spawn(
    LogActor.behavior(done, appenders, defaultLevel, defaultSlf4jLogLevel, defaultPekkoLogLevel),
    name = "LoggingActor",
    MailboxSelector.fromConfig("logging-dispatcher")
  )

  LoggingState.maybeLogActor = Some(logActor)

  private[logging] val gcLogger: Option[GcLogger] =
    if (gc) Some(new GcLogger)
    else None

  if (time) {
    // Start timing actor
    LoggingState.doTime = true
    val timeActor = system.spawn(new TimeActor(timeActorDonePromise).behavior, name = "TimingActor")
    LoggingState.timeActorOption = Some(timeActor)
  }
  else {
    timeActorDonePromise.success(())
  }

  /**
   * Get logging levels.
   *
   * @return the current and default logging levels.
   */
  def getDefaultLogLevel: Levels = Levels(LoggingState.logLevel, defaultLevel)

  /**
   * Changes the logger API logging level.
   *
   * @param level the new logging level for the logger API.
   */
  def setDefaultLogLevel(level: Level): Unit = {
    LoggingState.defaultLogLevel = level
    LoggingState.logLevel = level
  }

  /**
   * Get Pekko logging levels
   *
   * @return the current and default Pekko logging levels.
   */
  def getPekkoLevel: Levels = Levels(LoggingState.pekkoLogLevel, defaultPekkoLogLevel)

  /**
   * Changes the Pekko logger logging level.
   *
   * @param level the new logging level for the Pekko logger.
   */
  def setPekkoLevel(level: Level): Unit = {
    LoggingState.pekkoLogLevel = level
    logActor ! SetPekkoLevel(level)
  }

  /**
   * Get the Slf4j logging levels.
   *
   * @return the current and default Slf4j logging levels.
   */
  def getSlf4jLevel: Levels = Levels(LoggingState.slf4jLogLevel, defaultSlf4jLogLevel)

  /**
   * Changes the slf4j logging level.
   *
   * @param level the new logging level for slf4j.
   */
  def setSlf4jLevel(level: Level): Unit = {
    LoggingState.slf4jLogLevel = level
    logActor ! SetSlf4jLevel(level)
  }

  /**
   * Get the logging appenders.
   *
   * @return the current and default logging appenders.
   */
  def getAppenders: List[LogAppenderBuilder] = appenderBuilders

  /**
   * Changes the logging appenders.
   *
   * @param _appenderBuilders the list of new logging appenders.
   */
  def setAppenders(_appenderBuilders: List[LogAppenderBuilder]): Unit = {
    appenderBuilders = _appenderBuilders
    appenders = appenderBuilders.map {
      _.apply(system, standardHeaders)
    }
    logActor ! SetAppenders(appenders)
  }

  def addAppenders(_appenderBuilders: LogAppenderBuilder*): Unit =
    setAppenders(getAppenders ++ _appenderBuilders)

  def setComponentLogLevel(prefix: Prefix, level: Level): Unit =
    ComponentLoggingStateManager.add(prefix, level)

  /**
   * Get the basic logging configuration values
   *
   * @return LogMetadata which comprises of current root log level, pekko log level, sl4j log level and component log level
   */
  def getLogMetadata(prefix: Prefix): LogMetadata =
    LogMetadata(
      getDefaultLogLevel.current,
      getPekkoLevel.current,
      getSlf4jLevel.current,
      LoggingState.componentsLoggingState
        .getOrDefault(prefix, ComponentLoggingState(LoggingState.defaultLogLevel))
        .componentLogLevel
    )

  /**
   * Shut down the logging system.
   *
   * @return future completes when the logging system is shut down.
   */
  def stop: Future[Done] = {
    def stopPekko(): Future[Unit] = {
      MessageHandler.sendMsg(LastPekkoMessage)
      LoggingState.pekkoStopPromise.future
    }

    def stopTimeActor(): Future[Unit] = {
      LoggingState.timeActorOption foreach (timeActor => timeActor ! TimeDone)
      timeActorDonePromise.future
    }

    def stopLogger(): Future[Unit] = {
      LoggingState.loggerStopping = true
      logActor ! StopLogging
      LoggingState.maybeLogActor = None
      LoggingState.loggerStopping = false
      done.future
    }

    def finishAppenders(): Future[Unit] = Future.sequence(appenders map (_.finish())).map(_ => ())

    def stopAppenders(): Future[Unit] = Future.sequence(appenders map (_.stop())).map(_ => ())

    // Stop gc logger
    gcLogger.foreach(_.stop())

    // Stop Slf4j
    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    loggerContext.stop()

    for {
      _ <- stopPekko() zip stopTimeActor()
      _ <- finishAppenders()
      _ <- stopLogger()
      _ <- stopAppenders()
    } yield Done
  }

  def javaStop(): CompletableFuture[Done] = stop.asJava.toCompletableFuture

  private def getAppenderInstance(appender: String): LogAppenderBuilder = {
    try {
      if (appender.endsWith("$"))
        Class.forName(appender).getField("MODULE$").get(null).asInstanceOf[LogAppenderBuilder]
      else {
        val buf = ByteBuffer.allocateDirect(1)
        try {
          val directByteBufferConstr = buf.getClass.getDeclaredConstructor(classOf[Long], classOf[Int], classOf[Any])
          directByteBufferConstr.setAccessible(true)
        }
        catch {
          case e: Exception =>
        }
        Class.forName(appender).getDeclaredConstructor().newInstance().asInstanceOf[LogAppenderBuilder]
      }
    }
    catch {
      case _: Throwable => throw AppenderNotFoundException(appender)
    }
  }
}
