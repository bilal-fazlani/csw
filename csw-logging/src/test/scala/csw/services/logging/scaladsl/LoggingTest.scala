package csw.services.logging.scaladsl

import com.persist.JsonOps
import csw.services.logging.internal.LoggingLevels
import csw.services.logging.internal.LoggingLevels._
import csw.services.logging.utils.LoggingTestSuite
import org.scalatest.prop.TableDrivenPropertyChecks._

object TromboneHcdLogger      extends ComponentLogger("tromboneHcd")
object TromboneAssemblyLogger extends ComponentLogger("tromboneAssembly")
object InnerSourceLogger      extends ComponentLogger("InnerClass")

class TromboneHcd() extends TromboneHcdLogger.Simple {

  def startLogging(logs: Map[String, String], kind: String = "all"): Unit = kind match {
    case "trace"       ⇒ log.trace(logs("trace"))
    case "debug"       ⇒ log.debug(logs("debug"))
    case "info"        ⇒ log.info(logs("info"))
    case "warn"        ⇒ log.warn(logs("warn"))
    case "error"       ⇒ log.error(logs("error"))
    case "fatal"       ⇒ log.fatal(logs("fatal"))
    case "alternative" ⇒ log.alternative("some-alternative-category", Map("@msg" → logs("alternative")))
    case "all" ⇒ {
      log.trace(logs("trace"))
      log.debug(logs("debug"))
      log.info(logs("info"))
      log.warn(logs("warn"))
      log.error(logs("error"))
      log.fatal(logs("fatal"))
    }
  }

}

class TromboneAssembly() extends TromboneAssemblyLogger.Simple {
  def startLogging(logs: Map[String, String]): Unit = {
    log.trace(logs("trace"))
    log.debug(logs("debug"))
    log.info(logs("info"))
    log.warn(logs("warn"))
    log.error(logs("error"))
    log.fatal(logs("fatal"))
  }
}

object SingletonTest extends InnerSourceLogger.Simple {
  def startLogging(logs: Map[String, String]): Unit = {
    log.trace(logs("trace"))
    log.debug(logs("debug"))
    log.info(logs("info"))
    log.warn(logs("warn"))
    log.error(logs("error"))
    log.fatal(logs("fatal"))
    log.alternative("some-alternative-category", Map("@msg" → logs("alternative")))
  }
}

class InnerSourceTest extends InnerSourceLogger.Simple {
  def startLogging(logs: Map[String, String]): Unit = new InnerSource().startLogging(logs)
  class InnerSource {
    def startLogging(logs: Map[String, String]): Unit = {
      log.trace(logs("trace"))
      log.debug(logs("debug"))
      log.info(logs("info"))
      log.warn(logs("warn"))
      log.error(logs("error"))
      log.fatal(logs("fatal"))
    }
  }

}

class LoggingTest extends LoggingTestSuite {

  // DEOPSCSW-116: Make log messages identifiable with components
  // DEOPSCSW-121: Define structured tags for log messages
  test("component logs should contain component name") {
    new TromboneHcd().startLogging(logMsgMap)
    Thread.sleep(100)

    logBuffer.foreach { log ⇒
      log.contains("@componentName") shouldBe true
      log("@componentName") shouldBe "tromboneHcd"
    }

  }

  // DEOPSCSW-119: Associate source with each log message
  // DEOPSCSW-121: Define structured tags for log messages
  test("component logs should contain source location in terms of file name, class name and line number") {
    new TromboneHcd().startLogging(logMsgMap)
    Thread.sleep(100)
    logBuffer.foreach { log ⇒
      log("file") shouldBe "LoggingTest.scala"
      log.contains("line") shouldBe true
      log("class") shouldBe "csw.services.logging.scaladsl.TromboneHcd"
    }
  }

  // DEOPSCSW-119: Associate source with each log message
  // DEOPSCSW-121: Define structured tags for log messages
  test("inner class logs should contain source location in terms of file name, class name and line number") {
    new InnerSourceTest().startLogging(logMsgMap)
    Thread.sleep(100)
    logBuffer.foreach { log ⇒
      log("file") shouldBe "LoggingTest.scala"
      log.contains("line") shouldBe true
      log("class") shouldBe "csw.services.logging.scaladsl.InnerSourceTest$InnerSource"
    }
  }

  // DEOPSCSW-119: Associate source with each log message
  // DEOPSCSW-121: Define structured tags for log messages
  test("singleton object logs should contain source location in terms of file name, class name and line number") {
    SingletonTest.startLogging(logMsgMap)
    Thread.sleep(100)
    logBuffer.foreach { log ⇒
      log("file") shouldBe "LoggingTest.scala"
      log.contains("line") shouldBe true
      log("class") shouldBe "csw.services.logging.scaladsl.SingletonTest"
    }
  }

  // DEOPSCSW-126 : Configurability of logging characteristics for component / log instance
  test("should load default filter provided in configuration file and applied to normal logging messages") {

    //  TromboneHcd component is logging 6 messages
    //  As per the filter, hcd should log 5 message of all the levels except TRACE
    new TromboneHcd().startLogging(logMsgMap)
    Thread.sleep(200)

    //  TromboneHcd component is logging 6 messages each of unique level
    //  As per the filter, hcd should log 5 message of all level except TRACE
    logBuffer.size shouldBe 5

    val groupByComponentNamesLog = logBuffer.groupBy(json ⇒ json("@componentName").toString)
    val tromboneHcdLogs          = groupByComponentNamesLog("tromboneHcd")

    tromboneHcdLogs.size shouldBe 5

    // check that log level should be greater than or equal to debug and
    // assert on actual log message
    tromboneHcdLogs.toList.foreach { log ⇒
      val currentLogLevel = log("@severity").toString.toLowerCase
      val currentLogMsg   = log("message").toString
      Level(currentLogLevel) >= LoggingLevels.DEBUG shouldBe true
      currentLogMsg shouldBe logMsgMap(currentLogLevel)
    }
  }

  // DEOPSCSW-126 : Configurability of logging characteristics for component / log instance
  test("should apply default log level provided in configuration file for normal logging messages") {

    new TromboneAssembly().startLogging(logMsgMap)
    Thread.sleep(200)

    //  TromboneAssembly component is logging 6 messages each of unique level
    //  As per the default loglevel = trace, assembly should log all 6 message
    logBuffer.size shouldBe 6

    val groupByComponentNamesLog = logBuffer.groupBy(json ⇒ json("@componentName").toString)
    val tromboneAssemblyLogs     = groupByComponentNamesLog("tromboneAssembly")

    tromboneAssemblyLogs.size shouldBe 6

    // check that log level should be greater than or equal to debug and
    // assert on actual log message
    tromboneAssemblyLogs.toList.foreach { log ⇒
      val currentLogLevel = log("@severity").toString.toLowerCase
      val currentLogMsg   = log("message").toString
      Level(currentLogLevel) >= LoggingLevels.TRACE shouldBe true
      currentLogMsg shouldBe logMsgMap(currentLogLevel)
    }
  }

  // DEOPSCSW-124: Define severity levels for log messages
  // DEOPSCSW-125: Define severity levels for specific components/log instances
  test("should able to filter logs based on configured/updated log level (covers all levels)") {
    val testData = Table(
      ("logLevel", "expectedLogCount"),
      (FATAL, 1),
      (ERROR, 2),
      (WARN, 3),
      (INFO, 4),
      (DEBUG, 5),
      (TRACE, 6)
    )
    val compName = "tromboneAssembly"

    def filterLogsByComponentName(compName: String): Seq[JsonOps.JsonObject] = {
      val groupByComponentNamesLog = logBuffer.groupBy(json ⇒ json("@componentName").toString)
      groupByComponentNamesLog(compName)
    }

    forAll(testData) { (logLevel: Level, logCount: Int) =>
      loggingSystem.setLevel(logLevel)

      new TromboneAssembly().startLogging(logMsgMap)
      Thread.sleep(200)

      val tromboneAssemblyLogs = filterLogsByComponentName(compName)
      tromboneAssemblyLogs.size shouldBe logCount

      tromboneAssemblyLogs.toList.foreach { log ⇒
        val currentLogLevel = log("@severity").toString.toLowerCase
        val currentLogMsg   = log("message").toString
        Level(currentLogLevel) >= logLevel shouldBe true
        currentLogMsg shouldBe logMsgMap(currentLogLevel)
      }

      logBuffer.clear()
    }
  }

  test("alternative log message should contain @category") {
    new TromboneHcd().startLogging(logMsgMap, "alternative")
    Thread.sleep(100)

    logBuffer.foreach { log ⇒
      log("@category") shouldBe true
    }
  }

}
