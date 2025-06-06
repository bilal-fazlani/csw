/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.testkit.redis
import java.util.Optional

import org.apache.pekko.actor.typed.scaladsl.adapter.TypedActorSystemOps
import org.apache.pekko.actor.{ActorSystem, typed}
import org.apache.pekko.util.Timeout
import csw.location.api.scaladsl.{LocationService, RegistrationResult}
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.location.api.models.Connection.TcpConnection
import csw.location.api.models.TcpRegistration
import csw.network.utils.SocketUtils.getFreePort
import csw.testkit.internal.TestKitUtils
import redis.embedded.{RedisSentinel, RedisServer}

import scala.concurrent.ExecutionContext

private[testkit] trait RedisStore extends EmbeddedRedis {

  implicit def system: typed.ActorSystem[?]
  implicit def timeout: Timeout
  protected def masterId: String
  protected def connection: TcpConnection

  implicit lazy val untypedSystem: ActorSystem = system.toClassic
  implicit lazy val ec: ExecutionContext       = system.executionContext

  private var redisSentinel: Option[RedisSentinel] = None
  private var redisServer: Option[RedisServer]     = None

  private implicit lazy val locationService: LocationService = HttpLocationServiceFactory.makeLocalClient

  def start(
      sentinelPort: Int = getFreePort,
      serverPort: Int = getFreePort,
      keyspaceEvent: Boolean = false
  ): RegistrationResult = {
    val tuple = startSentinel(sentinelPort, serverPort, masterId, keyspaceEvent)
    redisSentinel = Some(tuple._1)
    redisServer = Some(tuple._2)
    val resultF = locationService.register(TcpRegistration(connection, sentinelPort))
    TestKitUtils.await(resultF, timeout)
  }

  def start(sentinelPort: Optional[Int], serverPort: Optional[Int]): Unit =
    start(sentinelPort.orElse(getFreePort), serverPort.orElse(getFreePort))

  def stopRedis(): Unit = {
    redisServer.foreach(_.stop())
    redisSentinel.foreach(_.stop())
  }

  def shutdown(): Unit = {
    stopRedis()
    TestKitUtils.shutdown({ system.terminate(); system.whenTerminated }, timeout)
  }
}
