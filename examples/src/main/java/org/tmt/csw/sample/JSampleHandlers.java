/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tmt.csw.sample;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.util.Timeout;
import csw.command.api.javadsl.ICommandService;
import csw.command.client.CommandServiceFactory;
import csw.command.client.messages.TopLevelActorMessage;
import csw.event.api.javadsl.IEventSubscription;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.models.JCswContext;
import csw.location.api.models.Location;
import csw.location.api.models.LocationRemoved;
import csw.location.api.models.LocationUpdated;
import csw.location.api.models.TrackingEvent;
import csw.logging.api.javadsl.ILogger;
import csw.params.commands.CommandName;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.commands.Setup;
import csw.params.core.generics.Key;
import csw.params.core.generics.Parameter;
import csw.params.core.models.Id;
import csw.params.core.models.ObsId;
import csw.params.events.Event;
import csw.params.events.EventKey;
import csw.params.events.EventName;
import csw.params.events.SystemEvent;
import csw.params.javadsl.JKeyType;
import csw.params.javadsl.JUnits;
import csw.prefix.javadsl.JSubsystem;
import csw.prefix.models.Prefix;
import csw.time.core.models.UTCTime;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to SampleHcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/framework.html
 */
public class JSampleHandlers extends JComponentHandlers {

    private final JCswContext cswCtx;
    private final ILogger log;
    private final ActorContext<TopLevelActorMessage> actorContext;
    private final ActorRef<WorkerCommand> commandSender;

    JSampleHandlers(ActorContext<TopLevelActorMessage> ctx, JCswContext cswCtx) {
        super(ctx, cswCtx);
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(getClass());
        this.actorContext = ctx;
        this.commandSender = createWorkerActor();
    }

    //#worker-actor
    private interface WorkerCommand {
    }

    private static final class SendCommand implements WorkerCommand {
        private final ICommandService hcd;

        private SendCommand(ICommandService hcd) {
            this.hcd = hcd;
        }
    }

    private ActorRef<WorkerCommand> createWorkerActor() {
        return actorContext.spawn(
                Behaviors.receiveMessage(msg -> {
                    if (msg instanceof SendCommand) {
                        SendCommand command = (SendCommand) msg;
                        log.trace("WorkerActor received SendCommand message.");
                        handle(Id.apply(), command.hcd);
                    } else {
                        log.error("Unsupported messsage type");
                    }
                    return Behaviors.same();
                }),
                "CommandSender"
        );
    }

    private void handle(Id runId, ICommandService hcd) {

        // Construct Setup command
        Key<Long> sleepTimeKey = JKeyType.LongKey().make("SleepTime", JUnits.millisecond);
        Parameter<Long> sleepTimeParam = sleepTimeKey.set(5000L);

        Setup setupCommand = new Setup(cswCtx.componentInfo().prefix(), new CommandName("sleep"), Optional.of(ObsId.apply("2018A-P001-O123"))).add(sleepTimeParam);

        Timeout commandResponseTimeout = new Timeout(10, TimeUnit.SECONDS);

        // Submit command and handle response
        hcd.submitAndWait(setupCommand, commandResponseTimeout)
                .exceptionally(ex -> new CommandResponse.Error(runId, "Exception occurred when sending command: " + ex.getMessage()))
                .thenAccept(commandResponse -> {
                    if (commandResponse instanceof CommandResponse.Locked) {
                        log.error("Sleed command failed: HCD is locked");
                    } else if (commandResponse instanceof CommandResponse.Invalid) {
                        CommandResponse.Invalid inv = (CommandResponse.Invalid) commandResponse;
                        log.error("Sleep command invalid (" + inv.issue().getClass().getSimpleName() + "): " + inv.issue().reason());
                    } else if (commandResponse instanceof CommandResponse.Error) {
                        CommandResponse.Error x = (CommandResponse.Error) commandResponse;
                        log.error(() -> "Command Completed with error: " + x.message());
                    } else if (commandResponse instanceof CommandResponse.Completed) {
                        log.info("Command completed successfully");
                    } else {
                        log.error("Command failed: ");
                    }
                });
    }
    //#worker-actor

    //#initialize
    private Optional<IEventSubscription> maybeEventSubscription = Optional.empty();

    @Override
    public void initialize() {
        log.info("In Assembly initialize");
        maybeEventSubscription = Optional.of(subscribeToHcd());
    }

    @Override
    public void onShutdown() {
        log.info("Assembly is shutting down.");
    }
    //#initialize

    //#track-location
    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
        log.debug(() -> "onLocationTrackingEvent called: " + trackingEvent.toString());
        if (trackingEvent instanceof LocationUpdated) {
            LocationUpdated updated = (LocationUpdated) trackingEvent;
            Location location = updated.location();
            ICommandService hcd = CommandServiceFactory.jMake(location, actorContext.getSystem());
            commandSender.tell(new SendCommand(hcd));
        } else if (trackingEvent instanceof LocationRemoved) {
            log.info("HCD no longer available");
        }
    }
    //#track-location

    //#subscribe
    private final EventKey counterEventKey = new EventKey(Prefix.apply(JSubsystem.CSW, "samplehcd"), new EventName("HcdCounter"));
    private final Key<Integer> hcdCounterKey = JKeyType.IntKey().make("counter", JUnits.NoUnits);

    private void processEvent(Event event) {
        log.info("Event received: " + event.eventKey());
        if (event instanceof SystemEvent) {
            SystemEvent sysEvent = (SystemEvent) event;
            if (event.eventKey().equals(counterEventKey)) {
                int counter = sysEvent.parameter(hcdCounterKey).head();
                log.info("Counter = " + counter);
            } else {
                log.warn("Unexpected event received.");
            }
        } else {
            // ObserveEvent, not expected
            log.warn("Unexpected ObserveEvent received.");
        }
    }

    private IEventSubscription subscribeToHcd() {
        log.info("Starting subscription.");
        return cswCtx.eventService().defaultSubscriber().subscribeCallback(Set.of(counterEventKey), this::processEvent);
    }

    private void unsubscribeHcd() {
        log.info("Stopping subscription.");
        maybeEventSubscription.ifPresent(IEventSubscription::unsubscribe);
    }
    //#subscribe

    @Override
    public CommandResponse.ValidateCommandResponse validateCommand(Id runId, ControlCommand controlCommand) {
        return null;
    }

    @Override
    public CommandResponse.SubmitResponse onSubmit(Id runId, ControlCommand controlCommand) {
        return new CommandResponse.Completed(runId);
    }

    @Override
    public void onOneway(Id runId, ControlCommand controlCommand) {
    }

    @Override
    public void onGoOffline() {
    }

    @Override
    public void onGoOnline() {
    }

    @Override
    public void onDiagnosticMode(UTCTime startTime, String hint) {
    }

    @Override
    public void onOperationsMode() {
    }
}
