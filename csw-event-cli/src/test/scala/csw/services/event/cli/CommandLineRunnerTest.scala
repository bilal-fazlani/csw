package csw.services.event.cli

import csw.messages.events._
import csw.messages.params.formats.JsonSupport
import csw.services.event.helpers.TestFutureExt.RichFuture
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar.convertDoubleToGrainOfTime
import org.scalatest.{FunSuite, Matchers}
import play.api.libs.json.Json

import scala.io.Source

class CommandLineRunnerTest extends FunSuite with Matchers with SeedData with Eventually {

  import cliWiring._

  test("should able to inspect event/events containing multiple parameters including recursive structs") {

    commandLineRunner.inspect(argsParser.parse(Seq("inspect", "-e", s"${event1.eventKey}")).get).await
    logBuffer.filterNot(_.startsWith("==")).toSet shouldEqual expectedOut1

    logBuffer.clear()

    commandLineRunner.inspect(argsParser.parse(Seq("inspect", "--events", s"${event2.eventKey}")).get).await
    logBuffer.filterNot(_.startsWith("==")).toSet shouldEqual expectedOut2

    logBuffer.clear()

    commandLineRunner.inspect(argsParser.parse(Seq("inspect", "-e", s"${event1.eventKey},${event2.eventKey}")).get).await
    logBuffer.filterNot(_.startsWith("==")).toSet shouldEqual (expectedOut1 ++ expectedOut2)
  }

  test("should able to get entire event/events in json format") {

    commandLineRunner.get(argsParser.parse(Seq("get", "-e", s"${event1.eventKey}", "-o", "json")).get).await
    JsonSupport.readEvent[SystemEvent](Json.parse(logBuffer.head)) shouldBe event1

    logBuffer.clear()

    commandLineRunner.get(argsParser.parse(Seq("get", "-e", s"${event1.eventKey},${event2.eventKey}", "--out", "json")).get).await
    val events = logBuffer.map(event ⇒ JsonSupport.readEvent[Event](Json.parse(event))).toSet
    events shouldEqual Set(event1, event2)
  }

  test("should able to publish event when event json file provided") {
    val path              = getClass.getResource("/observe_event.json").getPath
    val expectedEventJson = Json.parse(Source.fromResource("observe_event.json").mkString)

    val eventKey = "wfos.blue.filter.filter_wheel"
    commandLineRunner.publish(argsParser.parse(Seq("publish", "-e", s"$eventKey", "--data", path)).get).await

    eventually(timeout = timeout(1.second), interval = interval(100.millis)) {
      commandLineRunner.get(argsParser.parse(Seq("get", "-e", eventKey, "-o", "json")).get).await
      Json.parse(logBuffer.last) shouldBe expectedEventJson
    }
  }
}
