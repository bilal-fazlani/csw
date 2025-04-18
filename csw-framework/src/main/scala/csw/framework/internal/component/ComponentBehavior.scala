/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.framework.internal.component

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import csw.command.client.MiniCRM.MiniCRMMessage.AddStarted
import csw.command.client.messages.CommandMessage.{Oneway, Submit, Validate}
import csw.command.client.messages.DiagnosticDataMessage.{DiagnosticMode, OperationsMode}
import csw.command.client.messages.FromComponentLifecycleMessage.Running
import csw.command.client.messages.RunningMessage.Lifecycle
import csw.command.client.messages.TopLevelActorCommonMessage.{TrackingEventReceived, UnderlyingHookFailed}
import csw.command.client.messages.TopLevelActorIdleMessage.Initialize
import csw.command.client.messages.*
import csw.command.client.models.framework.LocationServiceUsage.RegisterAndTrackServices
import csw.command.client.models.framework.ToComponentLifecycleMessage
import csw.command.client.models.framework.ToComponentLifecycleMessage.{GoOffline, GoOnline}
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.logging.api.scaladsl.Logger
import csw.params.commands.CommandResponse.*
import csw.params.core.models.Id

import scala.concurrent.blocking
import scala.util.control.NonFatal

// scalastyle:off method.length
private[framework] object ComponentBehavior {

  /**
   * The Behavior of a component actor, represented as a mutable behavior
   *
   * @param supervisor the actor reference of the supervisor actor which created this component
   * @param lifecycleHandlers the implementation of handlers which defines the domain actions to be performed by this component
   * @param cswCtx holds instances of all the csw services
   */
  def make(
      supervisor: ActorRef[FromComponentLifecycleMessage],
      lifecycleHandlers: ComponentHandlers,
      cswCtx: CswContext
  ): Behavior[TopLevelActorMessage] =
    Behaviors.setup(factory = ctx => {
      import cswCtx.*
      val fiveChars   = 5
      val log: Logger = loggerFactory.getLogger(ctx)

      var lifecycleState: ComponentLifecycleState = ComponentLifecycleState.Idle

      ctx.self ! Initialize

      /*
       * Defines processing for a pekko.actor.typed.Signal received by the actor instance
       * @return the existing behavior
       */
      def onSignal: PartialFunction[(ActorContext[TopLevelActorMessage], Signal), Behavior[TopLevelActorMessage]] = {
        case (_, PostStop) =>
          log.warn("Component TLA is shutting down")
          try {
            log.info("Invoking lifecycle handler's onShutdown hook")
            blocking {
              lifecycleHandlers.onShutdown()
            }
          }
          catch {
            case NonFatal(throwable) => log.error(throwable.getMessage, ex = throwable)
          }
          Behaviors.same
      }

      /*
       * Defines action for messages which can be received in any ComponentLifecycleState state
       * @param commonMessage message representing a message received in any lifecycle state
       */
      def onCommon(commonMessage: TopLevelActorCommonMessage): Unit =
        commonMessage match {
          case UnderlyingHookFailed(exception) =>
            log.error(exception.getMessage, ex = exception)
            throw exception
          case TrackingEventReceived(trackingEvent) =>
            lifecycleHandlers.onLocationTrackingEvent(trackingEvent)
        }

      /*
       * Defines action for messages which can be received in [[ComponentLifecycleState.Idle]] state
       * @param idleMessage message representing a message received in [[ComponentLifecycleState.Idle]] state
       */
      def onIdle(idleMessage: TopLevelActorIdleMessage): Unit =
        idleMessage match {
          case Initialize =>
            try {
              log.info("Invoking lifecycle handler's initialize hook")
              blocking {
                lifecycleHandlers.initialize()
              }
              log.debug(
                s"Component TLA is changing lifecycle state from [$lifecycleState] to [${ComponentLifecycleState.Initialized}]"
              )
              lifecycleState = ComponentLifecycleState.Initialized
              // track all connections in component info for location updates
              if (componentInfo.locationServiceUsage == RegisterAndTrackServices) {
                componentInfo.connections.foreach(connection => {
                  locationService.subscribe(connection, trackingEvent => ctx.self ! TrackingEventReceived(trackingEvent))
                })
              }
              lifecycleState = ComponentLifecycleState.Running
              lifecycleHandlers.isOnline = true
              supervisor ! Running(ctx.self)
            }
            catch {
              case NonFatal(e) => ctx.self ! UnderlyingHookFailed(e)
            }
        }

      /*
       * Defines action for messages which can be received in [[ComponentLifecycleState.Running]] state
       *
       * @param runningMessage message representing a message received in [[ComponentLifecycleState.Running]] state
       */
      def onRun(runningMessage: RunningMessage): Unit =
        runningMessage match {
          case Lifecycle(message)       => onLifecycle(message)
          case x: CommandMessage        => onRunningCompCommandMessage(x)
          case x: DiagnosticDataMessage => onRunningCompDiagnosticDataMessage(x)
          case null                     => log.error(s"Component TLA cannot handle null message")
        }

      /*
       * Defines action for messages which alter the [[ComponentLifecycleState]] state
       *
       * @param toComponentLifecycleMessage message representing a lifecycle message sent by the supervisor to the component
       */
      def onLifecycle(toComponentLifecycleMessage: ToComponentLifecycleMessage): Unit =
        toComponentLifecycleMessage match {
          case GoOnline =>
            log.info("Invoking lifecycle handler's onGoOnline hook")
            lifecycleHandlers.onGoOnline()
            if (!lifecycleHandlers.isOnline) {
              lifecycleHandlers.isOnline = true
              log.debug(s"Component TLA is Online")
            }
            else {
              log.debug(s"Component TLA is already Online")
            }

          case GoOffline =>
            log.info("Invoking lifecycle handler's onGoOffline hook")
            lifecycleHandlers.onGoOffline()
            if (lifecycleHandlers.isOnline) {
              lifecycleHandlers.isOnline = false
              log.debug(s"Component TLA is Offline")
            }
            else {
              log.debug(s"Component TLA is already Offline")
            }
        }

      def onRunningCompDiagnosticDataMessage(diagnosticDataMessage: DiagnosticDataMessage): Unit =
        diagnosticDataMessage match {
          case DiagnosticMode(startTime, hint) => lifecycleHandlers.onDiagnosticMode(startTime, hint)
          case OperationsMode                  => lifecycleHandlers.onOperationsMode()
        }

      /*
       * Defines action for messages which represent a [[csw.params.commands.Command]]
       *
       * @param commandMessage message encapsulating a [[csw.params.commands.Command]]
       */
      def onRunningCompCommandMessage(commandMessage: CommandMessage): Unit =
        commandMessage match {
          case Validate(_, replyTo) => handleValidate(Id(), commandMessage, replyTo)
          case Oneway(_, replyTo)   => handleOneway(Id(), commandMessage, replyTo)
          case Submit(_, replyTo)   => handleSubmit(Id(), commandMessage, replyTo)
        }

      def handleValidate(runId: Id, commandMessage: CommandMessage, replyTo: ActorRef[ValidateResponse]): Unit = {
        val runIdStrSmall      = runId.id.takeRight(fiveChars)
        val validationResponse = lifecycleHandlers.validateCommand(runId, commandMessage.command)
        replyTo ! validationResponse.asInstanceOf[ValidateResponse]
        validationResponse match {
          case accepted: Accepted =>
            log.info(
              s"Validate:runId:[$runIdStrSmall] with response as $accepted for command name [${commandMessage.command.commandName}]"
            )
          case invalid: Invalid =>
            log.info(s"Validate:runId:[$runIdStrSmall] with msg [${invalid.issue}]")
        }

      }

      def handleOneway(runId: Id, commandMessage: CommandMessage, replyTo: ActorRef[OnewayResponse]): Unit = {
        val runIdStrSmall = runId.id.takeRight(fiveChars)
        log.info(s"Oneway:runId:[$runIdStrSmall] with msg [$commandMessage]")
        val validationResponse = lifecycleHandlers.validateCommand(runId, commandMessage.command)
        replyTo ! validationResponse.asInstanceOf[OnewayResponse]

        validationResponse match {
          case accepted: Accepted =>
            log.info(s"Oneway:runId:[$runIdStrSmall]  with response:[$accepted]")
            lifecycleHandlers.onOneway(runId, commandMessage.command)
          case invalid: Invalid =>
            log.info(s"Oneway:runId:[$runIdStrSmall] with [${invalid.issue}]-Command not forwarded to TLA post validation.")
        }
      }

      def handleSubmit(runId: Id, commandMessage: CommandMessage, replyTo: ActorRef[SubmitResponse]): Unit = {
        val runIdStrSmall = runId.id.takeRight(fiveChars)
        log.info(s"Submit:runId:[$runIdStrSmall] with msg[$commandMessage]")
        lifecycleHandlers.validateCommand(runId, commandMessage.command) match {
          case _: Accepted =>
            val submitResponse = lifecycleHandlers.onSubmit(runId, commandMessage.command)
            log.info(s"Submit:runId:[$runIdStrSmall] with handler response:${submitResponse.typeName}")
            submitResponse match {
              case started: Started =>
                commandResponseManager.commandResponseManagerActor ! AddStarted(started)
              case _ => // Do nothing
            }
            replyTo ! submitResponse
          case invalid: Invalid =>
            log.info(s"Validate:runId:[$runIdStrSmall] with [${invalid.issue}]")
            replyTo ! invalid
        }
      }

      /*
       * Defines processing for a [[TopLevelActorMessage]] received by the actor instance.
       *
       * @param msg componentMessage received from supervisor
       * @return the existing behavior
       */
      Behaviors
        .receiveMessage[TopLevelActorMessage] { msg =>
          log.debug(s"Component TLA in lifecycle state :[$lifecycleState] received message :[$msg]")
          (lifecycleState, msg) match {
            case (_, msg: TopLevelActorCommonMessage)                          => onCommon(msg)
            case (ComponentLifecycleState.Idle, msg: TopLevelActorIdleMessage) => onIdle(msg)
            case (ComponentLifecycleState.Running, msg: RunningMessage)        => onRun(msg)
            case _ =>
              log.error(s"Unexpected message :[$msg] received by component in lifecycle state :[$lifecycleState]")
          }
          Behaviors.same
        }
        .receiveSignal(onSignal)

    })

}
