/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tmt.csw.samplehcd;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.SpawnProtocol;
import org.apache.pekko.util.Timeout;
import csw.command.api.javadsl.ICommandService;
import csw.command.client.CommandServiceFactory;
import csw.event.api.javadsl.IEventService;
import csw.event.api.javadsl.IEventSubscriber;
import csw.event.api.javadsl.IEventSubscription;
import csw.location.api.javadsl.ILocationService;
import csw.location.api.javadsl.JComponentType;
import csw.location.api.models.PekkoLocation;
import csw.location.api.models.ComponentId;
import csw.location.api.models.Connection.PekkoConnection;
import csw.params.commands.CommandName;
import csw.params.commands.CommandResponse;
import csw.params.commands.Setup;
import csw.params.core.generics.Key;
import csw.params.core.generics.Parameter;
import csw.params.core.models.ObsId;
import csw.params.events.Event;
import csw.params.events.EventKey;
import csw.params.events.EventName;
import csw.params.events.SystemEvent;
import csw.params.javadsl.JKeyType;
import csw.params.javadsl.JUnits;
import csw.prefix.javadsl.JSubsystem;
import csw.prefix.models.Prefix;
import csw.testkit.javadsl.FrameworkTestKitJunitResource;
import csw.testkit.javadsl.JCSWService;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

//#setup
public class JSampleHcdTest {

    @ClassRule
    public static final FrameworkTestKitJunitResource testKit =
            new FrameworkTestKitJunitResource(Arrays.asList(JCSWService.AlarmServer, JCSWService.EventServer));


    @BeforeClass
    public static void setup() {
        testKit.spawnStandalone(com.typesafe.config.ConfigFactory.load("JSampleHcdStandalone.conf"));
    }

    @Test
    public void testHCDShouldBeLocatableUsingLocationService() throws ExecutionException, InterruptedException {
        PekkoConnection connection = new PekkoConnection(new ComponentId(Prefix.apply(JSubsystem.CSW, "samplehcd"), JComponentType.HCD));
        ILocationService locationService = testKit.jLocationService();
        PekkoLocation location = locationService.resolve(connection, Duration.ofSeconds(10)).get().orElseThrow();

        Assert.assertEquals(connection, location.connection());
    }
//#setup

    //#subscribe
    @Test
    public void testShouldBeAbleToSubscribeToHCDEvents() throws InterruptedException, ExecutionException {
        EventKey counterEventKey = new EventKey(Prefix.apply("csw.samplehcd"), new EventName("HcdCounter"));
        Key<Integer> hcdCounterKey = JKeyType.IntKey().make("counter", JUnits.NoUnits);
        IEventService eventService = testKit.jEventService();
        IEventSubscriber subscriber = eventService.defaultSubscriber();

        // wait for a bit to ensure HCD has started and published an event
        Thread.sleep(3000);

        ArrayList<Event> subscriptionEventList = new ArrayList<>();
        IEventSubscription subscription = subscriber.subscribeCallback(Set.of(counterEventKey), event -> {
            // discard invalid event
            if (!event.isInvalid()) {
                subscriptionEventList.add(event);
            }
        });
        subscription.ready().get();

        // Sleep for 5 seconds, to allow HCD to publish events
        Thread.sleep(5000);

        // Q. Why expected count is either 3 or 4?
        // A. Total sleep = 7 seconds (2 + 5), subscriber listens for all the events between 2-7 seconds
        //  1)  If HCD publish starts at 1st second
        //      then events published at 1, 3, 5, 7, 9 etc. seconds
        //      In this case, subscriber will receive events at 2(initial), 3, 5, 7, i.e. total 4 events
        //  2)  If HCD publish starts at 1.5th seconds
        //      then events published at 1.5, 3.5, 5.5, 7.5, 9.5 etc. seconds
        //      In this case, subscriber will receive events at 2(initial), 3.5, 5.5, i.e. total 3 events
        int recEventsCount = subscriptionEventList.size();
        Assert.assertTrue("expected:<3> or <4> but was:<" + recEventsCount + ">", recEventsCount == 3 || recEventsCount == 4);

        // extract counter values to a List for comparison
        List<Integer> counterList = subscriptionEventList.stream()
                .map(ev -> {
                    SystemEvent sysEv = ((SystemEvent) ev);
                    if (sysEv.contains(hcdCounterKey)) {
                        return sysEv.parameter(hcdCounterKey).head();
                    } else {
                        return -1;
                    }
                })
                .collect(Collectors.toList());

        // we don't know exactly how long HCD has been running when this test runs,
        // so we don't know what the first value will be,
        // but we know we should get three consecutive numbers
        int counter0 = counterList.get(0);
        List<Integer> expectedCounterList = Arrays.asList(counter0, counter0 + 1, counter0 + 2);

        Assert.assertEquals(expectedCounterList, counterList);
    }
    //#subscribe

    //#submitAndWait
    private final ActorSystem<SpawnProtocol.Command> typedActorSystem = testKit.actorSystem();

    // DEOPSCSW-39: examples of Location Service
    @Test
    public void testShouldBeAbleToSendSleepCommandToHCD() throws ExecutionException, InterruptedException, TimeoutException {

        // Construct Setup command
        Key<Long> sleepTimeKey = JKeyType.LongKey().make("SleepTime", JUnits.millisecond);
        Parameter<Long> sleepTimeParam = sleepTimeKey.set(1000L);

        Setup setupCommand = new Setup(Prefix.apply(JSubsystem.CSW, "move"), new CommandName(("sleep")), Optional.of(ObsId.apply("2020A-001-123"))).add(sleepTimeParam);

        Timeout commandResponseTimeout = new Timeout(5, TimeUnit.SECONDS);

        PekkoConnection connection = new PekkoConnection(new ComponentId(Prefix.apply(JSubsystem.CSW, "samplehcd"), JComponentType.HCD));
        ILocationService locationService = testKit.jLocationService();
        PekkoLocation location = locationService.resolve(connection, Duration.ofSeconds(5)).get().orElseThrow();

        ICommandService hcd = CommandServiceFactory.jMake(location, typedActorSystem);

        CommandResponse.SubmitResponse result = hcd.submitAndWait(setupCommand, commandResponseTimeout).get(5, TimeUnit.SECONDS);
        Assert.assertTrue(result instanceof CommandResponse.Completed);
    }
    //#submitAndWait

    //#exception

    @Test
    public void testShouldGetExecutionExceptionIfSubmitTimeoutIsTooSmall() throws ExecutionException, InterruptedException {

        // Construct Setup command
        Key<Long> sleepTimeKey = JKeyType.LongKey().make("SleepTime", JUnits.millisecond);
        Parameter<Long> sleepTimeParam = sleepTimeKey.set(5000L);

        Setup setupCommand = new Setup(Prefix.apply(JSubsystem.CSW, "move"), new CommandName("sleep"), Optional.of(ObsId.apply("2020A-001-123"))).add(sleepTimeParam);

        Timeout commandResponseTimeout = new Timeout(1, TimeUnit.SECONDS);

        PekkoConnection connection = new PekkoConnection(new ComponentId(Prefix.apply(JSubsystem.CSW, "samplehcd"), JComponentType.HCD));
        ILocationService locationService = testKit.jLocationService();
        PekkoLocation location = locationService.resolve(connection, Duration.ofSeconds(10)).get().orElseThrow();

        ICommandService hcd = CommandServiceFactory.jMake(location, typedActorSystem);

        Assert.assertThrows(ExecutionException.class, () -> hcd.submitAndWait(setupCommand, commandResponseTimeout).get());
    }
//#exception
}
