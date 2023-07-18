/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.framework.javadsl

import org.apache.pekko.actor.typed.javadsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.JCswContext
import csw.framework.scaladsl.ComponentHandlers

/**
 * Base class for component handlers which will be used by the component actor
 *
 * @param ctx the [[pekko.actor.typed.javadsl.ActorContext]] under which the actor instance of the component, which use these handlers, is created
 * @param cswCtx provides access to csw services e.g. location, event, alarm, etc
 */
abstract class JComponentHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: JCswContext)
    extends ComponentHandlers(ctx.asScala, cswCtx.asScala) {}
