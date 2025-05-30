/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.location

import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.scaladsl.{Keep, Sink}
import csw.location.api.models.Connection.{PekkoConnection, HttpConnection, TcpConnection}
import csw.location.api.models.*
import csw.location.api.{PekkoRegistrationFactory, models}
import csw.logging.client.commons.PekkoTypedExtension.UserActorFactory
import csw.prefix.models.{Prefix, Subsystem}

class TrackLocationTestMultiJvmNode1 extends TrackLocationTest(0, "cluster")

class TrackLocationTestMultiJvmNode2 extends TrackLocationTest(0, "cluster")

class TrackLocationTestMultiJvmNode3 extends TrackLocationTest(0, "cluster")

class TrackLocationTest(ignore: Int, mode: String) extends helpers.LSNodeSpec(config = new helpers.TwoMembersAndSeed, mode) {

  import config._

  // DEOPSCSW-26: Track a connection
  test(
    s"${testPrefixWithSuite} two components should able to track same connection and single component should able to track two components | DEOPSCSW-26, DEOPSCSW-429"
  ) {
    //create pekko connection
    val pekkoConnection = PekkoConnection(models.ComponentId(Prefix(Subsystem.NFIRAOS, "tromboneHcd"), ComponentType.HCD))

    //create http connection
    val httpConnection = HttpConnection(models.ComponentId(Prefix(Subsystem.NFIRAOS, "Assembly1"), ComponentType.Assembly))

    //create tcp connection
    val tcpConnection = TcpConnection(ComponentId(Prefix(Subsystem.CSW, "redis1"), ComponentType.Service))

    runOn(seed) {
      val actorRef = clusterSettings.system.spawn(Behaviors.empty, "trombone-hcd")
      locationService.register(PekkoRegistrationFactory.make(pekkoConnection, actorRef)).await
      enterBarrier("Registration")

      locationService.unregister(pekkoConnection).await
      enterBarrier("Pekko-unregister")
      enterBarrier("Http-unregister")
      enterBarrier("Tcp-unregister")
    }

    runOn(member1) {
      val port   = 5656
      val prefix = "/trombone/hcd"

      val httpRegistration       = HttpRegistration(httpConnection, port, prefix)
      val httpRegistrationResult = locationService.register(httpRegistration).await
      val pekkoProbe              = TestProbe[TrackingEvent]("test-probe1")
      val tcpProbe               = TestProbe[TrackingEvent]("test-probe2")

      locationService.track(pekkoConnection).toMat(Sink.foreach(pekkoProbe.ref.tell(_)))(Keep.left).run()
      locationService.track(tcpConnection).toMat(Sink.foreach(tcpProbe.ref.tell(_)))(Keep.left).run()

      val pekkoEvent             = pekkoProbe.expectMessageType[LocationUpdated]
      val trackedPekkoConnection = pekkoEvent.asInstanceOf[LocationUpdated].connection
      trackedPekkoConnection shouldBe pekkoConnection

      val tcpEvent: LocationUpdated = tcpProbe.expectMessageType[LocationUpdated]
      tcpEvent.connection shouldBe tcpConnection

      enterBarrier("Registration")
      enterBarrier("Pekko-unregister")

      val pekkoRemovedEvent: LocationRemoved = pekkoProbe.expectMessageType[LocationRemoved]
      pekkoRemovedEvent.connection shouldBe pekkoConnection

      httpRegistrationResult.unregister().await
      enterBarrier("Http-unregister")
      enterBarrier("Tcp-unregister")

      val tcpRemovedEvent: LocationRemoved = tcpProbe.expectMessageType[LocationRemoved]
      tcpRemovedEvent.connection shouldBe tcpConnection
    }

    runOn(member2) {
      val Port                  = 5657
      val tcpRegistration       = TcpRegistration(tcpConnection, Port)
      val tcpRegistrationResult = locationService.register(tcpRegistration).await

      val httpProbe = TestProbe[TrackingEvent]("test-probe")

      locationService.track(httpConnection).toMat(Sink.foreach(httpProbe.ref.tell(_)))(Keep.left).run()

      val httpEvent: TrackingEvent = httpProbe.expectMessageType[LocationUpdated]
      httpEvent.connection shouldBe httpConnection

      enterBarrier("Registration")
      enterBarrier("Pekko-unregister")
      enterBarrier("Http-unregister")

      val httpRemovedEvent: TrackingEvent = httpProbe.expectMessageType[LocationRemoved]
      httpRemovedEvent.connection shouldBe httpConnection

      tcpRegistrationResult.unregister().await
      enterBarrier("Tcp-unregister")
    }

    enterBarrier("after-2")
  }
}
