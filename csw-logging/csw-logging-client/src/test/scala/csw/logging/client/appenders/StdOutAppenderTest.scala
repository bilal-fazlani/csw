/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.logging.client.appenders

import java.io.ByteArrayOutputStream

import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import com.typesafe.config.ConfigFactory
import csw.logging.client.commons.{Category, LoggingKeys}
import csw.logging.client.internal.JsonExtensions.RichJsObject
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

// DEOPSCSW-122: Allow local component logs to be output to STDOUT
class StdOutAppenderTest extends AnyFunSuite with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  private val actorSystem = ActorSystem(SpawnProtocol(), "test-1")

  private val standardHeaders: JsObject = Json.obj(
    LoggingKeys.VERSION -> 1,
    LoggingKeys.EX      -> "localhost",
    LoggingKeys.SERVICE -> Json.obj("name" -> "test-service", "version" -> "1.2.3")
  )

  private val stdOutAppender = new StdOutAppender(actorSystem, standardHeaders, println)

  private val logMessage: String =
    s"""{
      |  "${LoggingKeys.COMPONENT_NAME}": "FileAppenderTest",
      |  "${LoggingKeys.SUBSYSTEM}": "csw",
      |  "${LoggingKeys.PREFIX}": "csw.FileAppenderTest",
      |  "${LoggingKeys.HOST}": "localhost",
      |  "${LoggingKeys.SERVICE}": {
      |    "name": "logging",
      |    "version": "SNAPSHOT-1.0"
      |  },
      |  "${LoggingKeys.SEVERITY}": "ERROR",
      |  "${LoggingKeys.TIMESTAMP}": "2017-06-19T16:10:19.397Z",
      |  "${LoggingKeys.VERSION}": 1,
      |  "${LoggingKeys.CLASS}": "csw.logging.client.appenders.FileAppenderTest",
      |  "${LoggingKeys.FILE}": "FileAppenderTest.scala",
      |  "${LoggingKeys.LINE}": 25,
      |  "${LoggingKeys.MESSAGE}": "This is at ERROR level",
      |  "${LoggingKeys.PLAINSTACK}": "exceptions.AppenderNotFoundException at csw.logging.Main (Main.scala 19)"
      |}
    """.stripMargin

  private val expectedLogJson = Json.parse(logMessage).as[JsObject]

  private val outCapture = new ByteArrayOutputStream

  override protected def afterEach(): Unit = {
    outCapture.reset()
  }

  override protected def afterAll(): Unit = {
    outCapture.close()
    actorSystem.terminate()
    Await.result(actorSystem.whenTerminated, 5.seconds)
  }

  test("should print message to standard output stream if category is \'common\' | DEOPSCSW-122") {

    Console.withOut(outCapture) {
      stdOutAppender.append(expectedLogJson, Category.Common.name)
    }

    val actualLogJson = Json.parse(outCapture.toString).as[JsObject]
    actualLogJson shouldBe expectedLogJson
  }

  test("should not print message to standard output stream if category is not \'common\' | DEOPSCSW-122") {
    val category = "foo"

    Console.withOut(outCapture) {
      stdOutAppender.append(expectedLogJson, category)
    }

    outCapture.toString.isEmpty shouldBe true
  }

  // DEOPSCSW-325: Include exception stack trace in stdout log message for exceptions
  // CSW-78: PrefixRedesign for logging
  test("should able to pretty-print one log message to one line | DEOPSCSW-122, DEOPSCSW-325") {

    val config = ConfigFactory
      .parseString("csw-logging.appender-config.stdout.oneLine=true")
      .withFallback(ConfigFactory.load())

    val actorSystemWithOneLineTrueConfig = ActorSystem(SpawnProtocol(), "test-2", config)
    val stdOutAppenderForOneLineMsg      = new StdOutAppender(actorSystemWithOneLineTrueConfig, standardHeaders, println)

    Console.withOut(outCapture) {
      stdOutAppenderForOneLineMsg.append(expectedLogJson, Category.Common.name)
    }

    val actualOneLineLogMsg = outCapture.toString.replace("\n", "")
    val severity            = expectedLogJson.getString(LoggingKeys.SEVERITY)
    val msg                 = expectedLogJson.getString(LoggingKeys.MESSAGE)
    val fileName            = expectedLogJson.getString(LoggingKeys.FILE)
    val lineNumber          = expectedLogJson.getString(LoggingKeys.LINE)
    val plainStack          = expectedLogJson.getString(LoggingKeys.PLAINSTACK)
    val timestamp           = expectedLogJson.getString(LoggingKeys.TIMESTAMP)
    val prefix              = expectedLogJson.getString(LoggingKeys.PREFIX)
    val expectedOneLineLogMsg =
      f"$timestamp $severity%-5s $prefix ($fileName $lineNumber) - $msg [Stacktrace] $plainStack"

    actualOneLineLogMsg shouldBe expectedOneLineLogMsg

    actorSystemWithOneLineTrueConfig.terminate()
    Await.result(actorSystemWithOneLineTrueConfig.whenTerminated, 5.seconds)
  }

}
