package org.tmt.csw.sample

import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.CSW
import csw.testkit.scaladsl.CSWService.{AlarmServer, EventServer}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.Await

//#intro
class SampleTest extends ScalaTestFrameworkTestKit(AlarmServer, EventServer) with AnyFunSuiteLike {
  import frameworkTestKit.frameworkWiring._
  //#intro

  //#setup
  override def beforeAll(): Unit = {
    super.beforeAll()
    spawnStandalone(com.typesafe.config.ConfigFactory.load("SampleStandalone.conf"))
  }
  //#setup

  //#locate
  import scala.concurrent.duration._
  test("Assembly should be locatable using Location Service") {
    val connection   = AkkaConnection(ComponentId(Prefix(CSW, "sample"), ComponentType.Assembly))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe connection
  }
  //#locate
}
