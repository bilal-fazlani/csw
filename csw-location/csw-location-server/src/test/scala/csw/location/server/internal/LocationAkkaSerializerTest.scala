package csw.location.server.internal

import java.net.URI

import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.actor.typed.{ActorSystem, Behavior}
import akka.serialization.SerializationExtension
import csw.location.api.extensions.ActorExtension.RichActor
import csw.location.model.scaladsl
import csw.location.model.scaladsl.ComponentType.Assembly
import csw.location.model.scaladsl.Connection.{AkkaConnection, HttpConnection, TcpConnection}
import csw.location.model.scaladsl._
import csw.params.core.models.Prefix
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables.Table
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

private[location] class LocationAkkaSerializerTest extends FunSuite with Matchers with BeforeAndAfterAll {

  // need to instantiate from remote factory to wire up serializer
  private final implicit val system: ActorSystem[_] = ActorSystem(Behavior.empty, "example")
  private final val serialization                   = SerializationExtension(system.toUntyped)
  private final val prefix                          = Prefix("wfos.prog.cloudcover")

  override protected def afterAll(): Unit = {
    system.terminate()
    Await.result(system.whenTerminated, 2.seconds)
  }

  test("should use location serializer for Connection (de)serialization") {
    val testData = Table(
      "Connection models",
      AkkaConnection(ComponentId("TromboneAssembly", Assembly)),
      HttpConnection(scaladsl.ComponentId("TromboneAssembly", Assembly)),
      TcpConnection(scaladsl.ComponentId("TromboneAssembly", Assembly))
    )

    forAll(testData) { connection ⇒
      val serializer = serialization.findSerializerFor(connection)
      serializer.getClass shouldBe classOf[LocationAkkaSerializer]

      val bytes = serializer.toBinary(connection)
      serializer.fromBinary(bytes, Some(connection.getClass)) shouldEqual connection
    }
  }

  test("should use location serializer for Location (de)serialization") {
    val akkaConnection = AkkaConnection(scaladsl.ComponentId("TromboneAssembly", Assembly))
    val httpConnection = HttpConnection(scaladsl.ComponentId("TromboneAssembly", Assembly))
    val tcpConnection  = TcpConnection(scaladsl.ComponentId("TromboneAssembly", Assembly))
    val testData = Table(
      "Location models",
      AkkaLocation(akkaConnection, prefix, system.toURI),
      HttpLocation(httpConnection, new URI("")),
      TcpLocation(tcpConnection, new URI(""))
    )

    forAll(testData) { location ⇒
      val serializer = serialization.findSerializerFor(location)
      serializer.getClass shouldBe classOf[LocationAkkaSerializer]

      val bytes = serializer.toBinary(location)
      serializer.fromBinary(bytes, Some(location.getClass)) shouldEqual location
    }
  }

  test("should use location serializer for Registration (de)serialization") {
    val akkaConnection = AkkaConnection(scaladsl.ComponentId("TromboneAssembly", Assembly))
    val httpConnection = HttpConnection(scaladsl.ComponentId("TromboneAssembly", Assembly))
    val tcpConnection  = TcpConnection(scaladsl.ComponentId("TromboneAssembly", Assembly))
    val testData = Table(
      "Registration models",
      AkkaRegistration(akkaConnection, prefix, system.toURI),
      HttpRegistration(httpConnection, 1234, ""),
      TcpRegistration(tcpConnection, 1234)
    )

    forAll(testData) { registration ⇒
      val serializer = serialization.findSerializerFor(registration)
      serializer.getClass shouldBe classOf[LocationAkkaSerializer]

      val bytes = serializer.toBinary(registration)
      serializer.fromBinary(bytes, Some(registration.getClass)) shouldEqual registration
    }
  }

  test("should use location serializer for TrackingEvent (de)serialization") {
    val akkaConnection = AkkaConnection(scaladsl.ComponentId("TromboneAssembly", Assembly))
    val akkaLocation   = AkkaLocation(akkaConnection, prefix, system.toURI)

    val testData = Table(
      "TrackingEvent models",
      LocationUpdated(akkaLocation),
      LocationRemoved(akkaConnection)
    )

    forAll(testData) { trackingEvent ⇒
      val serializer = serialization.findSerializerFor(trackingEvent)
      serializer.getClass shouldBe classOf[LocationAkkaSerializer]

      val bytes = serializer.toBinary(trackingEvent)
      serializer.fromBinary(bytes, Some(trackingEvent.getClass)) shouldEqual trackingEvent
    }
  }
}