/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.framework.integration

import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.SpawnProtocol
import org.apache.pekko.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.command.client.CommandServiceFactory
import csw.command.client.extensions.PekkoLocationExt.RichPekkoLocation
import csw.command.client.messages.SupervisorContainerCommonMessages.Shutdown
import csw.command.client.models.framework.{ContainerLifecycleState, SupervisorLifecycleState}
import csw.common.FrameworkAssertions.*
import csw.common.components.framework.SampleComponentState.*
import csw.event.client.helpers.TestFutureExt.given
import scala.language.implicitConversions

import csw.framework.internal.wiring.{Container, FrameworkWiring, Standalone}
import csw.location.api
import csw.location.api.models.ComponentType.{Assembly, HCD}
import csw.location.api.models.Connection.PekkoConnection
import csw.location.api.models.{ComponentId, HttpRegistration, TcpRegistration}
import csw.location.client.ActorSystemFactory
import csw.params.commands
import csw.params.commands.CommandName
import csw.params.core.states.{CurrentState, StateName}
import csw.prefix.models.{Prefix, Subsystem}
import io.lettuce.core.RedisClient

import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationLong

//CSW-82: ComponentInfo should take prefix
class TrackConnectionsIntegrationTest extends FrameworkIntegrationSuite {
  import testWiring.seedLocationService

  private val filterAssemblyConnection = PekkoConnection(api.models.ComponentId(Prefix(Subsystem.TCS, "Filter"), Assembly))
  private val disperserHcdConnection   = PekkoConnection(api.models.ComponentId(Prefix(Subsystem.TCS, "Disperser"), HCD))

  override def afterAll(): Unit = {
    super.afterAll()
  }

  // DEOPSCSW-218: Discover component connection information using Pekko protocol
  // DEOPSCSW-220: Access and Monitor components for current values
  // DEOPSCSW-221: Avoid sending commands to non-executing components
  test(
    "should track connections when locationServiceUsage is RegisterAndTrackServices | DEOPSCSW-218, DEOPSCSW-220, DEOPSCSW-221"
  ) {
    implicit val containerActorSystem = ActorSystemFactory.remote(SpawnProtocol(), "test1")
    val wiring: FrameworkWiring       = FrameworkWiring.make(containerActorSystem, mock[RedisClient])

    // start a container and verify it moves to running lifecycle state
    val containerRef = Container.spawn(ConfigFactory.load("container_tracking_connections.conf"), wiring).await

    val containerLifecycleStateProbe = TestProbe[ContainerLifecycleState]("container-lifecycle-state-probe")(containerActorSystem)
    val assemblyProbe                = TestProbe[CurrentState]("assembly-state-probe")

    // initially container is put in Idle lifecycle state and wait for all the components to move into Running lifecycle state
    // ********** Message: GetContainerLifecycleState **********
    assertThatContainerIsRunning(containerRef, containerLifecycleStateProbe, 5.seconds)

    // resolve all the components from container using location service
    val filterAssemblyLocation = wiring.locationService.find(filterAssemblyConnection).await
    val disperserHcdLocation   = wiring.locationService.find(disperserHcdConnection).await

    val assemblyCommandService = CommandServiceFactory.make(filterAssemblyLocation.get)(containerActorSystem)

    val disperserComponentRef   = disperserHcdLocation.get.componentRef
    val disperserCommandService = CommandServiceFactory.make(disperserHcdLocation.get)(containerActorSystem)

    // Subscribe to component's current state
    val subscription = assemblyCommandService.subscribeCurrentState(assemblyProbe.ref ! _)

    // assembly is tracking two HCD's, hence assemblyProbe will receive LocationUpdated event from two HCD's
    assemblyProbe.expectMessage(
      5.seconds,
      CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(pekkoLocationUpdatedChoice)))
    )
    assemblyProbe.expectMessage(
      5.seconds,
      CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(pekkoLocationUpdatedChoice)))
    )

    // if one of the HCD shuts down, then assembly should know and receive LocationRemoved event
    disperserComponentRef ! Shutdown
    assemblyProbe.expectMessage(
      5.seconds,
      CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(pekkoLocationRemovedChoice)))
    )

    implicit val timeout: Timeout = Timeout(100.millis)
    a[TimeoutException] shouldBe thrownBy(
      disperserCommandService.submitAndWait(commands.Setup(prefix, CommandName("isAlive"), None)).await(200.millis)
    )

    subscription.cancel()
    containerActorSystem.terminate()
    containerActorSystem.whenTerminated.await
  }

  /**
   * If component writer wants to track additional connections which are not specified in configuration file,
   * then using trackConnection(connection: Connection) hook which is added in ComponentHandlers can be used
   * Uses of this are shown in [[csw.common.components.framework.SampleComponentHandlers]]
   */
  // DEOPSCSW-219 Discover component connection using HTTP protocol
  test("component should be able to track http and tcp connections | DEOPSCSW-219") {
    implicit val componentActorSystem = ActorSystemFactory.remote(SpawnProtocol(), "test2")
    val wiring: FrameworkWiring       = FrameworkWiring.make(componentActorSystem, mock[RedisClient])
    // start component in standalone mode
    val assemblySupervisor = Standalone.spawn(ConfigFactory.load("standalone.conf"), wiring).await

    val supervisorLifecycleStateProbe =
      TestProbe[SupervisorLifecycleState]("supervisor-lifecycle-state-probe")(componentActorSystem)
    val pekkoConnection = PekkoConnection(ComponentId(Prefix(Subsystem.IRIS, "IFS_Detector"), HCD))

    assertThatSupervisorIsRunning(assemblySupervisor, supervisorLifecycleStateProbe, 5.seconds)

    val resolvedPekkoLocation = wiring.locationService.resolve(pekkoConnection, 5.seconds).await.value
    resolvedPekkoLocation.connection shouldBe pekkoConnection

    val assemblyProbe          = TestProbe[CurrentState]("assembly-state-probe")
    val assemblyCommandService = CommandServiceFactory.make(resolvedPekkoLocation)(componentActorSystem)
    // Subscribe to component's current state
    assemblyCommandService.subscribeCurrentState(assemblyProbe.ref ! _)

    // register http connection
    seedLocationService.register(HttpRegistration(httpConnection, 9090, "test/path")).await

    // assembly is tracking HttpConnection that we registered above, hence assemblyProbe will receive LocationUpdated event
    assemblyProbe.expectMessage(
      5.seconds,
      CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(httpLocationUpdatedChoice)))
    )

    // On unavailability of HttpConnection, the assembly should know and receive LocationRemoved event
    seedLocationService.unregister(httpConnection)
    assemblyProbe.expectMessage(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(httpLocationRemovedChoice))))

    // register tcp connection
    seedLocationService.register(TcpRegistration(tcpConnection, 9090)).await

    // assembly is tracking TcpConnection that we registered above, hence assemblyProbe will receive LocationUpdated event.
    assemblyProbe.expectMessage(
      5.seconds,
      CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(tcpLocationUpdatedChoice)))
    )

    // On unavailability of TcpConnection, the assembly should know and receive LocationRemoved event
    seedLocationService.unregister(tcpConnection)
    assemblyProbe.expectMessage(
      5.seconds,
      CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(tcpLocationRemovedChoice)))
    )

    componentActorSystem.terminate()
    componentActorSystem.whenTerminated.await
  }

}
