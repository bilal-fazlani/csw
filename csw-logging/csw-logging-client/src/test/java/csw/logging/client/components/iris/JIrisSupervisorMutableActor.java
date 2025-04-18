/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.logging.client.components.iris;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.BehaviorBuilder;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import csw.logging.api.javadsl.ILogger;
import csw.logging.client.LogCommand;
import csw.logging.client.javadsl.JLoggerFactory;
import csw.prefix.models.Prefix;

public class JIrisSupervisorMutableActor {

    public static Behavior<LogCommand> irisBeh(Prefix prefix) {
        JLoggerFactory loggerFactory = new JLoggerFactory(prefix);
        return new JIrisSupervisorMutableActor().behavior(loggerFactory);
    }

    public Behavior<LogCommand> behavior(JLoggerFactory loggerFactory){

        return Behaviors.setup(actorContext -> {
            ILogger log = loggerFactory.getLogger(actorContext, getClass());

            return BehaviorBuilder.<LogCommand>create()
                    .onMessage(LogCommand.class,
                            command -> command == LogCommand.LogTrace$.MODULE$,
                            (command) -> {
                                log.trace(command.toString());
                                return Behaviors.same();
                            })
                    .onMessage(LogCommand.class,
                            command -> command == LogCommand.LogDebug$.MODULE$,
                            (command) -> {
                                log.debug(command.toString());
                                return Behaviors.same();
                            })
                    .onMessage(LogCommand.class,
                            command -> command == LogCommand.LogInfo$.MODULE$,
                            (command) -> {
                                log.info(command.toString());
                                return Behaviors.same();
                            })
                    .onMessage(LogCommand.class,
                            command -> command == LogCommand.LogWarn$.MODULE$,
                            (command) -> {
                                log.warn(command.toString());
                                return Behaviors.same();
                            })
                    .onMessage(LogCommand.class,
                            command -> command == LogCommand.LogError$.MODULE$,
                            (command) -> {
                                log.error(command.toString());
                                return Behaviors.same();
                            })
                    .onMessage(LogCommand.class,
                            command -> command == LogCommand.LogFatal$.MODULE$,
                            (command) -> {
                                log.fatal(command.toString());
                                return Behaviors.same();
                            })
                    .build();
        });
    }
}
