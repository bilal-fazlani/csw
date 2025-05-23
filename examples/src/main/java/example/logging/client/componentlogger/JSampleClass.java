/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package example.logging.client.componentlogger;

import org.apache.pekko.actor.typed.javadsl.ActorContext;
import csw.command.client.messages.ComponentMessage;
import csw.logging.api.javadsl.ILogger;
import csw.logging.client.javadsl.JLoggerFactory;
import csw.logging.client.scaladsl.LoggerFactory;
import csw.prefix.models.Prefix;

//#component-logger-class
public class JSampleClass {

    public JSampleClass(JLoggerFactory loggerFactory) {
        ILogger log = loggerFactory.getLogger(getClass());
    }
}
//#component-logger-class

//#component-logger-actor
class JSampleActor extends org.apache.pekko.actor.AbstractActor {

    public JSampleActor(JLoggerFactory loggerFactory) {

        //context() is available from pekko.actor.AbstractActor
        ILogger log = loggerFactory.getLogger(context(), getClass());
    }

    @Override
    public Receive createReceive() {
        return null;
    }
}
//#component-logger-actor

//#component-logger-typed-actor
class JSampleTypedActor {

    public JSampleTypedActor(JLoggerFactory loggerFactory, ActorContext<ComponentMessage> ctx) {
        ILogger log = loggerFactory.getLogger(ctx, getClass());
    }
}
//#component-logger-typed-actor

class JSample {

    public void dummyMethod() {
        //#logger-factory-creation
        JLoggerFactory jLoggerFactory = new JLoggerFactory(Prefix.apply("csw.my-component-name"));

        // convert a java JLoggerFactory to scala LoggerFactory
        LoggerFactory loggerFactory = jLoggerFactory.asScala();
        //#logger-factory-creation
    }
}