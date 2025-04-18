/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tmt.csw.sample

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.command.client.messages.TopLevelActorMessage
import csw.event.api.scaladsl.EventSubscription
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.{PekkoLocation, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.params.commands.CommandResponse.*
import csw.params.commands.{CommandName, CommandResponse, ControlCommand, Setup}
import csw.params.core.generics.{Key, KeyType, Parameter}
import csw.params.core.models.{Id, ObsId, Units}
import csw.params.events.*
import csw.prefix.models.Prefix
import csw.time.core.models.UTCTime

import cps.compat.FutureAsync.*
import scala.concurrent.{ExecutionContextExecutor, Future}

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to SampleHcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/framework.html
 */
class SampleHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger

  // #worker-actor
  sealed trait WorkerCommand
  case class SendCommand(hcd: CommandService) extends WorkerCommand

  private val commandSender =
    ctx.spawn(
      Behaviors.receiveMessage[WorkerCommand](msg => {
        msg match {
          case command: SendCommand =>
            log.trace(s"WorkerActor received SendCommand message.")
            handle(command.hcd)
          case null => log.error("Unsupported message type")
        }
        Behaviors.same
      }),
      "CommandSender"
    )

  import scala.concurrent.duration._
  private implicit val sleepCommandTimeout: Timeout = Timeout(10000.millis)
  def handle(hcd: CommandService): Unit = {

    // Construct Setup command
    val sleepTimeKey: Key[Long]         = KeyType.LongKey.make("SleepTime")
    val sleepTimeParam: Parameter[Long] = sleepTimeKey.set(5000).withUnits(Units.millisecond)
    val setupCommand = Setup(componentInfo.prefix, CommandName("sleep"), Some(ObsId("2020A-001-123"))).add(sleepTimeParam)

    // submit command and handle response
    hcd.submitAndWait(setupCommand).onComplete {
      case scala.util.Success(value) =>
        value match {
          case _: CommandResponse.Locked => log.error("Sleep command failed: HCD is locked.")
          case inv: CommandResponse.Invalid =>
            log.error(s"Command is invalid: (${inv.issue.getClass.getSimpleName}): ${inv.issue.reason}")
          case x: CommandResponse.Error     => log.error(s"Command Completed with error: ${x.message}")
          case _: CommandResponse.Completed => log.info("Command completed successfully")
          case _                            => log.error("Command failed")
        }
      case scala.util.Failure(ex) => log.error(s"Exception occured when sending command: ${ex.getMessage}")
    }
  }
  // #worker-actor

  def handleUsingAsync(hcd: CommandService): Unit = {

    // Construct Setup command
    val sleepTimeKey: Key[Long]         = KeyType.LongKey.make("SleepTime")
    val sleepTimeParam: Parameter[Long] = sleepTimeKey.set(5000).withUnits(Units.millisecond)
    val setupCommand = Setup(componentInfo.prefix, CommandName("sleep"), Some(ObsId("2020A-001-123"))).add(sleepTimeParam)

    async {
      await(hcd.submitAndWait(setupCommand)) match {
        case _: CommandResponse.Locked    => log.error("HCD is locked.")
        case inv: CommandResponse.Invalid => log.error(s"Command is invalid: ${inv.issue.reason}")
        case x: CommandResponse.Error     => log.error(s"Command Completed with error: ${x.message}")
        case _: CommandResponse.Completed => log.info("Command completed successfully")
        case _                            => log.error("Command failed")
      }
    } recover { case ex: RuntimeException =>
      log.error(s"Command failed: ${ex.getMessage}")
    }
  }

  def handleUsingMap(runId: Id, hcd: CommandService): Unit = {

    // Construct Setup command
    val sleepTimeKey: Key[Long]         = KeyType.LongKey.make("SleepTime")
    val sleepTimeParam: Parameter[Long] = sleepTimeKey.set(5000).withUnits(Units.millisecond)
    val setupCommand = Setup(componentInfo.prefix, CommandName("sleep"), Some(ObsId("2020A-001-123"))).add(sleepTimeParam)

    // Submit command, and handle validation response. Final response is returned as a Future
    val submitCommandResponseF: Future[SubmitResponse] = hcd.submitAndWait(setupCommand).map {
      case x @ (Invalid(_, _) | Locked(_)) =>
        log.error("Sleep command invalid")
        x
      case x =>
        x
    } recover { case ex: RuntimeException =>
      CommandResponse.Error(runId, ex.getMessage)
    }

    // Wait for final response, and log result
    submitCommandResponseF.foreach {
      case _: CommandResponse.Completed => log.info("Command completed successfully")
      case x: CommandResponse.Error     => log.error(s"Command Completed with error: ${x.message}")
      case _                            => log.error("Command failed")
    }

  }

  // #initialize
  private var maybeEventSubscription: Option[EventSubscription] = None
  override def initialize(): Unit = {
    log.info("In Assembly initialize")
    maybeEventSubscription = Some(subscribeToHcd())
  }

  override def onShutdown(): Unit = {
    log.info("Assembly is shutting down.")
  }
  // #initialize

  // #track-location
  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    log.debug(s"onLocationTrackingEvent called: $trackingEvent")
    trackingEvent match {
      case LocationUpdated(location) =>
        val hcd = CommandServiceFactory.make(location.asInstanceOf[PekkoLocation])(ctx.system)
        commandSender ! SendCommand(hcd)
      case LocationRemoved(_) => log.info("HCD no longer available")
    }
  }
  // #track-location

  // #subscribe
  private val counterEventKey = EventKey(Prefix("CSW.samplehcd"), EventName("HcdCounter"))
  private val hcdCounterKey   = KeyType.IntKey.make("counter")

  private def processEvent(event: Event): Unit = {
    log.info(s"Event received: ${event.eventKey}")
    event match {
      case e: SystemEvent =>
        e.eventKey match {
          case `counterEventKey` =>
            val counter = e(hcdCounterKey).head
            log.info(s"Counter = $counter")
          case _ => log.warn("Unexpected event received.")
        }
      case e: ObserveEvent => log.warn("Unexpected ObserveEvent received.") // not expected
    }
  }

  private def subscribeToHcd(): EventSubscription = {
    log.info("Starting subscription.")
    eventService.defaultSubscriber.subscribeCallback(Set(counterEventKey), processEvent)
  }

  private def unsubscribeHcd(): Unit = {
    log.info("Stopping subscription.")
    maybeEventSubscription.foreach(_.unsubscribe())
  }
  // #subscribe

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = Accepted(runId)

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = Completed(runId)

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}
}
