/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.time.scheduler.api;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.testkit.typed.javadsl.ManualTime;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.typed.internal.adapter.ActorSystemAdapter;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import csw.time.core.models.TAITime;
import csw.time.scheduler.TimeServiceSchedulerFactory;
import org.junit.Rule;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class JTimeServiceSchedulerTest {

    @Rule
    public final TestKitJunitResource testKit = new TestKitJunitResource(ManualTime.config());

    private final org.apache.pekko.actor.typed.ActorSystem<Void> typedSystem = testKit.system();
    private final ManualTime manualTime = ManualTime.get(typedSystem);

    private final TimeServiceScheduler timeServiceScheduler =
            new TimeServiceSchedulerFactory(typedSystem.scheduler()).make(typedSystem.executionContext());

    private final ActorSystem untypedSystem = ActorSystemAdapter.toClassic(typedSystem);
    private final TestKit untypedTestKit = new TestKit(untypedSystem);

    //------------------------------Scheduling-------------------------------

    // DEOPSCSW-542: Schedule a task to execute in future
    // DEOPSCSW-549: Time service api
    @Test
    public void should_schedule_task_at_start_time__DEOPSCSW_542_DEOPSCSW_549() {
        TestProbe testProbe = new TestProbe(untypedSystem);
        String probeMsg = "some message";

        TAITime idealScheduleTime = new TAITime(TAITime.now().value().plusSeconds(1));

        Runnable task = () -> testProbe.ref().tell(probeMsg, ActorRef.noSender());

        Cancellable cancellable = timeServiceScheduler.scheduleOnce(idealScheduleTime, task);

        manualTime.timePasses(Duration.ofSeconds(1));
        testProbe.expectMsg(probeMsg);
        cancellable.cancel();
    }

    // DEOPSCSW-543: Notification on Scheduled timer expiry
    // DEOPSCSW-549: Time service api
    @Test
    public void should_schedule_message_at_start_time__DEOPSCSW_543_DEOPSCSW_549() {
        TestProbe testProbe = new TestProbe(untypedSystem);
        String probeMsg = "some message";

        TAITime idealScheduleTime = new TAITime(TAITime.now().value().plusSeconds(1));

        Cancellable cancellable = timeServiceScheduler.scheduleOnce(idealScheduleTime, testProbe.ref(), probeMsg);

        manualTime.timePasses(Duration.ofSeconds(1));
        testProbe.expectMsg(probeMsg);
        cancellable.cancel();
    }

    // DEOPSCSW-544: Schedule a task to be executed repeatedly
    // DEOPSCSW-547: Cancel scheduled timers for periodic tasks
    // DEOPSCSW-549: Time service api
    @Test
    public void should_schedule_a_task_periodically_at_given_interval__DEOPSCSW_544_DEOPSCSW_547_DEOPSCSW_549() {
        List<String> list = new ArrayList<>();

        Cancellable cancellable = timeServiceScheduler.schedulePeriodically(Duration.ofMillis(100), () -> list.add("x"));

        manualTime.timePasses(Duration.ofMillis(500));
        cancellable.cancel();

        assertEquals(list.size(), 6);
    }

    // DEOPSCSW-546: Notification on Scheduled Periodic timer expiry
    // DEOPSCSW-549: Time service api
    @Test
    public void should_schedule_a_message_periodically_at_given_interval__DEOPSCSW_546_DEOPSCSW_549() {
        Cancellable cancellable = timeServiceScheduler.schedulePeriodically(Duration.ofMillis(100), untypedTestKit.getRef(), "echo");

        manualTime.timePasses(Duration.ofMillis(500));
        cancellable.cancel();

        // total 6 msg's expected, more than this will fail test
        List<String> allMsgs = untypedTestKit.expectMsgAllOf("echo", "echo", "echo", "echo", "echo", "echo");
        assertEquals(allMsgs.size(), 6);
    }

    //DEOPSCSW-545: Start a repeating task with initial offset
    //DEOPSCSW-547: Cancel scheduled timers for periodic tasks
    // DEOPSCSW-549: Time service api
    @Test
    public void should_schedule_a_task_periodically_at_given_interval_after_start_time__DEOPSCSW_545_DEOPSCSW_547_DEOPSCSW_549() {
        List<String> list = new ArrayList<>();

        TAITime startTime = new TAITime(TAITime.now().value().plusSeconds(1));

        Cancellable cancellable = timeServiceScheduler.schedulePeriodically(startTime, Duration.ofMillis(100), () -> list.add("x"));

        manualTime.timePasses(Duration.ofSeconds(1));
        assertEquals(list.size(), 1);

        manualTime.timePasses(Duration.ofMillis(500));
        cancellable.cancel();
        assertEquals(list.size(), 6);
    }

    // DEOPSCSW-546: Notification on Scheduled Periodic timer expiry
    // DEOPSCSW-549: Time service api
    @Test
    public void should_schedule_a_message_periodically_at_given_interval_after_start_time__DEOPSCSW_546_DEOPSCSW_549() {
        TAITime startTime = new TAITime(TAITime.now().value().plusSeconds(1));

        Cancellable cancellable = timeServiceScheduler.schedulePeriodically(startTime, Duration.ofMillis(100), untypedTestKit.getRef(), "echo");

        manualTime.timePasses(Duration.ofSeconds(1));
        untypedTestKit.expectMsg("echo");

        manualTime.timePasses(Duration.ofMillis(500));
        cancellable.cancel();
        List<String> allMsgs = untypedTestKit.expectMsgAllOf("echo", "echo", "echo", "echo", "echo");
        assertEquals(allMsgs.size(), 5);
    }

    //DEOPSCSW-547: Cancel scheduled timers for single scheduled tasks
    // DEOPSCSW-549: Time service api
    @Test
    public void should_cancel_single_scheduled_task__DEOPSCSW_547_DEOPSCSW_549() {
        TestProbe testProbe = new TestProbe(untypedSystem);
        String probeMsg = "some message";
        TAITime idealScheduleTime = new TAITime(TAITime.now().value().plusSeconds(1));

        Runnable task = () -> testProbe.ref().tell(probeMsg, ActorRef.noSender());

        Cancellable cancellable = timeServiceScheduler.scheduleOnce(idealScheduleTime, task);
        cancellable.cancel();
        testProbe.expectNoMessage(FiniteDuration.apply(500, TimeUnit.MILLISECONDS));
    }
}
