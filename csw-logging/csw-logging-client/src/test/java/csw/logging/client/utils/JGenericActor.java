/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.logging.client.utils;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import csw.logging.api.javadsl.ILogger;
import csw.logging.client.javadsl.JGenericLoggerFactory;

public class JGenericActor {

    public static final Behavior<String> behavior =
            Behaviors.setup(context -> {
                // DEOPSCSW-316: Improve Logger accessibility for component developers
                final ILogger log = JGenericLoggerFactory.getLogger(context, JGenericActor.class);
                return Behaviors.receiveMessage(
                        msg -> {
                            switch (msg) {
                                case "trace":
                                    log.trace(() -> msg);
                                    break;
                                case "debug":
                                    log.debug(() -> msg);
                                    break;
                                case "info":
                                    log.info(() -> msg);
                                    break;
                                case "warn":
                                    log.warn(() -> msg);
                                    break;
                                case "error":
                                    log.error(() -> msg);
                                    break;
                                case "fatal":
                                    log.fatal(() -> msg);
                                    break;

                            }
                            return Behaviors.same();
                        });
            });

}
