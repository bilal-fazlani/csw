/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.framework.integration

import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.scaladsl.adapter.TypedActorSystemOps
import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.testkit.scaladsl.TestSink
import com.typesafe.config.ConfigFactory
import csw.command.client.CommandServiceFactory
import csw.command.client.extensions.PekkoLocationExt.RichPekkoLocation
import csw.command.client.messages.SupervisorContainerCommonMessages.Shutdown
import csw.command.client.models.framework.SupervisorLifecycleState
import csw.common.FrameworkAssertions.*
import csw.common.components.framework.SampleComponentHandlers
import csw.common.components.framework.SampleComponentState.*
import csw.common.utils.TestAppender
import csw.event.client.helpers.TestFutureExt.given
import scala.language.implicitConversions

import csw.framework.internal.component.ComponentBehavior
import csw.framework.internal.wiring.{FrameworkWiring, Standalone}
import csw.location.api.models.ComponentType.HCD
import csw.location.api.models.Connection.PekkoConnection
import csw.location.api.models.{ComponentId, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.location.client.ActorSystemFactory
import csw.logging.client.internal.LoggingSystem
import csw.logging.models.Level.INFO
import csw.params.core.states.{CurrentState, StateName}
import csw.prefix.models.{Prefix, Subsystem}
import io.lettuce.core.RedisClient
import play.api.libs.json.{JsObject, Json}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationLong

// DEOPSCSW-167: Creation and Deployment of Standalone Components
// DEOPSCSW-177: Hooks for lifecycle management
// DEOPSCSW-216: Locate and connect components to send PEKKO commands
// CSW-82: ComponentInfo should take prefix
// CSW-86: Subsystem should be case-insensitive
class StandaloneComponentTest extends FrameworkIntegrationSuite {
  import testWiring._
  // all log messages will be captured in log buffer
  private val logBuffer                    = mutable.Buffer.empty[JsObject]
  private val testAppender                 = new TestAppender(x => logBuffer += Json.parse(x.toString).as[JsObject])
  private var loggingSystem: LoggingSystem = scala.compiletime.uninitialized
  // using standaloneActorSystem to start component instead of seedActorSystem,
  // to assert shutdown of the component(which will also shutdown standaloneActorSystem)
  private val standaloneComponentActorSystem: ActorSystem[SpawnProtocol.Command] =
    ActorSystemFactory.remote(SpawnProtocol(), "test")

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    loggingSystem = new LoggingSystem("standalone", "1.0", "localhost", seedActorSystem)
    loggingSystem.setAppenders(List(testAppender))
  }

  override def afterAll(): Unit = {
    standaloneComponentActorSystem.terminate()
    standaloneComponentActorSystem.whenTerminated.await
    super.afterAll()
  }

  // DEOPSCSW-181: Multiple Examples for Lifecycle Support
  test(
    "should start a component in standalone mode and register with location service | DEOPSCSW-167, DEOPSCSW-177, DEOPSCSW-216, DEOPSCSW-181, DEOPSCSW-153, DEOPSCSW-180"
  ) {
    // start component in standalone mode
    val wiring: FrameworkWiring = FrameworkWiring.make(standaloneComponentActorSystem, mock[RedisClient])
    Standalone.spawn(ConfigFactory.load("standalone.conf"), wiring)

    val supervisorLifecycleStateProbe = TestProbe[SupervisorLifecycleState]("supervisor-lifecycle-state-probe")
    val supervisorStateProbe          = TestProbe[CurrentState]("supervisor-state-probe")
    val pekkoConnection               = PekkoConnection(ComponentId(Prefix(Subsystem.IRIS, "IFS_Detector"), HCD))

    // verify component gets registered with location service
    val maybeLocation = seedLocationService.resolve(pekkoConnection, 5.seconds).await

    maybeLocation.isDefined shouldBe true
    val resolvedPekkoLocation = maybeLocation.get
    resolvedPekkoLocation.connection shouldBe pekkoConnection

    val supervisorRef = resolvedPekkoLocation.componentRef
    assertThatSupervisorIsRunning(supervisorRef, supervisorLifecycleStateProbe, 5.seconds)

    val supervisorCommandService = CommandServiceFactory.make(resolvedPekkoLocation)

    val (_, pekkoProbe) =
      seedLocationService.track(pekkoConnection).toMat(TestSink.probe[TrackingEvent](seedActorSystem.toClassic))(Keep.both).run()
    pekkoProbe.requestNext() shouldBe a[LocationUpdated]

    // on shutdown, component unregisters from location service
    supervisorCommandService.subscribeCurrentState(supervisorStateProbe.ref ! _)
    supervisorRef ! Shutdown

    // this proves that ComponentBehaviors postStop signal gets invoked
    // as onShutdownHook of TLA gets invoked from postStop signal
    supervisorStateProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(shutdownChoice))))

    // this proves that postStop signal of supervisor gets invoked
    // as supervisor gets unregistered in postStop signal
    pekkoProbe.requestNext(10.seconds) shouldBe LocationRemoved(pekkoConnection)

    // this proves that on shutdown message, supervisor's actor system gets terminated
    // if it does not get terminated in 5 seconds, future will fail which in turn fail this test
    Await.result(standaloneComponentActorSystem.whenTerminated, 5.seconds)

    /*
     * This assertion are here just to prove that LoggingSystem is integrated with framework and ComponentHandlers
     * are able to log messages
     */
    // DEOPSCSW-153: Accessibility of logging service to other CSW components
    // DEOPSCSW-180: Generic and Specific Log messages
    assertThatMessageIsLogged(
      logBuffer,
      "IRIS",
      "IFS_Detector",
      "Invoking lifecycle handler's initialize hook",
      INFO,
      ComponentBehavior.getClass.getName
    )
    // log message from Component handler
    assertThatMessageIsLogged(
      logBuffer,
      "IRIS",
      "IFS_Detector",
      "Initializing Component TLA",
      INFO,
      classOf[SampleComponentHandlers].getName
    )
  }
}
