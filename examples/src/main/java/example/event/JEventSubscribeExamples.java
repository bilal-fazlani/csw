/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package example.event;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.stream.javadsl.Sink;
import csw.command.client.messages.TopLevelActorMessage;
import csw.event.api.javadsl.IEventSubscriber;
import csw.event.api.javadsl.IEventSubscription;
import csw.event.api.scaladsl.SubscriptionModes;
import csw.event.client.internal.commons.javawrappers.JEventService;
import csw.location.api.models.PekkoLocation;
import csw.params.events.Event;
import csw.params.events.EventKey;
import csw.params.events.EventName;
import csw.prefix.models.Subsystem;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class JEventSubscribeExamples {

    private final JEventService eventService;
    private final PekkoLocation hcdLocation;
    private final ActorSystem<Void> system;

    public JEventSubscribeExamples(JEventService eventService, PekkoLocation hcdLocation, ActorSystem<Void> system) {
        this.eventService = eventService;
        this.hcdLocation = hcdLocation;
        this.system = system;
    }

    public IEventSubscription callback() {
        //#with-callback

        IEventSubscriber subscriber = eventService.defaultSubscriber();

        EventKey filterWheelEventKey = new EventKey(hcdLocation.prefix(), new EventName("filter_wheel"));
        return subscriber.subscribeCallback(Set.of(filterWheelEventKey), event -> { /*do something*/ });

        //#with-callback
    }

    //#with-async-callback
    public IEventSubscription subscribe() {
        IEventSubscriber subscriber = eventService.defaultSubscriber();

        EventKey filterWheelEventKey = new EventKey(hcdLocation.prefix(), new EventName("filter_wheel"));
        return subscriber.subscribeAsync(Set.of(filterWheelEventKey), this::callback);
    }

    private CompletableFuture<String> callback(Event event) {
        /* do something */
        return CompletableFuture.completedFuture("some value");
    }
    //#with-async-callback

    //#with-actor-ref
    public IEventSubscription subscribe(ActorContext<TopLevelActorMessage> ctx) {

        IEventSubscriber subscriber = eventService.defaultSubscriber();
        ActorRef<Event> eventHandler = ctx.spawnAnonymous(JEventHandler.behavior());

        EventKey filterWheelEventKey = new EventKey(hcdLocation.prefix(), new EventName("filter_wheel"));
        return subscriber.subscribeActorRef(Set.of(filterWheelEventKey), eventHandler);
    }

    public static class JEventHandler {

        public static Behavior<Event> behavior() {
            // handle messages
            return null;
        }
    }
    //#with-actor-ref


    public IEventSubscription source() {
        //#with-source

        IEventSubscriber subscriber = eventService.defaultSubscriber();

        EventKey filterWheelEventKey = new EventKey(hcdLocation.prefix(), new EventName("filter_wheel"));
        return subscriber.subscribe(Set.of(filterWheelEventKey)).to(Sink.foreach(event -> { /*do something*/ })).run(system);

        //#with-source
    }

    public IEventSubscription subscriptionMode() {
        //#with-subscription-mode

        IEventSubscriber subscriber = eventService.defaultSubscriber();

        EventKey filterWheelEventKey = new EventKey(hcdLocation.prefix(), new EventName("filter_wheel"));
        return subscriber.subscribeCallback(Set.of(filterWheelEventKey), event -> { /* do something*/ }, Duration.ofMillis(1000), SubscriptionModes.jRateAdapterMode());

        //#with-subscription-mode
    }


    private void subscribeToSubsystemEvents(Subsystem subsystem) {
        // #psubscribe

        IEventSubscriber subscriber = eventService.defaultSubscriber();
        subscriber.pSubscribeCallback(subsystem, "*", event -> { /* do something*/ });

        // #psubscribe
    }

}
