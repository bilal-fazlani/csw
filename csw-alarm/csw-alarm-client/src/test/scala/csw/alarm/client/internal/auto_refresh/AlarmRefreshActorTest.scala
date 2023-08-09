/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.alarm.client.internal.auto_refresh

import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, ManualTime, TestProbe}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, SpawnProtocol}
import com.typesafe.config.ConfigFactory
import csw.alarm.models.AlarmSeverity.Major
import csw.alarm.models.AutoRefreshSeverityMessage
import csw.alarm.models.AutoRefreshSeverityMessage.{AutoRefreshSeverity, CancelAutoRefresh, SetSeverity}
import csw.alarm.models.Key.AlarmKey
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.NFIRAOS
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.DurationDouble

// DEOPSCSW-491: Auto-refresh an alarm through alarm service cli
// DEOPSCSW-507: Auto-refresh utility for component developers
// CSW-83: Alarm models should take prefix
class AlarmRefreshActorTest extends AnyFunSuiteLike with Eventually with Matchers with BeforeAndAfterAll {

  private val config                             = ManualTime.config.withFallback(ConfigFactory.load())
  implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(SpawnProtocol(), "test", config)
  private val testKit                            = ActorTestKit(actorSystem)
  import actorSystem.executionContext

  val tromboneAxisHighLimitAlarmKey = AlarmKey(Prefix(NFIRAOS, "trombone"), "tromboneAxisHighLimitAlarm")
  val tcsAxisHighLimitAlarmKey      = AlarmKey(Prefix(NFIRAOS, "tcs"), "tromboneAxisHighLimitAlarm")
  private val manualTime            = ManualTime()

  private def send[T](msg: T, to: ActorRef[T]): Future[Done] = Future { to ! msg; Done }

  test("should refresh severity | DEOPSCSW-491, DEOPSCSW-507") {
    val probe = TestProbe[String]()
    val actor = testKit.spawn(
      Behaviors.withTimers[AutoRefreshSeverityMessage](t =>
        AlarmRefreshActor.behavior(t, (_, _) => send("severity set", probe.ref), 5.seconds)
      )
    )
    actor ! SetSeverity(tcsAxisHighLimitAlarmKey, Major)
    probe.expectMessage("severity set")
  }

  test("should set severity and refresh it | DEOPSCSW-491, DEOPSCSW-507") {
    val probe = TestProbe[String]()
    val actor = testKit.spawn(
      Behaviors.withTimers[AutoRefreshSeverityMessage](t =>
        AlarmRefreshActor.behavior(t, (_, _) => send("severity refreshed", probe.ref), 5.seconds)
      )
    )

    actor ! AutoRefreshSeverity(tcsAxisHighLimitAlarmKey, Major)

    probe.expectMessage("severity refreshed")
    manualTime.timePasses(5.seconds)
    probe.expectMessage("severity refreshed")
  }

  test("should cancel the refreshing of alarm severity | DEOPSCSW-491, DEOPSCSW-507") {
    val probe = TestProbe[String]()
    val actor = testKit.spawn(
      Behaviors.withTimers[AutoRefreshSeverityMessage](t =>
        AlarmRefreshActor.behavior(t, (_, _) => send("severity refreshed", probe.ref), 5.seconds)
      )
    )

    actor ! AutoRefreshSeverity(tcsAxisHighLimitAlarmKey, Major)

    probe.expectMessage("severity refreshed")
    manualTime.timePasses(5.seconds)
    probe.expectMessage("severity refreshed")

    actor ! CancelAutoRefresh(tcsAxisHighLimitAlarmKey)
    manualTime.expectNoMessageFor(10.seconds)
  }

  test("should refresh for multiple alarms | DEOPSCSW-491, DEOPSCSW-507") {
    val queue: mutable.Queue[AlarmKey] = mutable.Queue.empty[AlarmKey]

    val actor = testKit.spawn(
      Behaviors.withTimers[AutoRefreshSeverityMessage](t =>
        AlarmRefreshActor.behavior(t, (key, _) => { queue.enqueue(key); Future.successful(Done) }, 5.seconds)
      )
    )

    actor ! AutoRefreshSeverity(tcsAxisHighLimitAlarmKey, Major)
    actor ! AutoRefreshSeverity(tromboneAxisHighLimitAlarmKey, Major)

    eventually(queue shouldEqual mutable.Queue(tcsAxisHighLimitAlarmKey, tromboneAxisHighLimitAlarmKey))

    actor ! CancelAutoRefresh(tcsAxisHighLimitAlarmKey)

    manualTime.timePasses(5.seconds)

    eventually(
      queue shouldEqual mutable.Queue(tcsAxisHighLimitAlarmKey, tromboneAxisHighLimitAlarmKey, tromboneAxisHighLimitAlarmKey)
    )
  }

}
