package csw.trombone.assembly.actors

import akka.typed.ActorRef
import akka.typed.scaladsl.ActorContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.messages.CommandMessage.Submit
import csw.messages.CommandValidationResponse.Accepted
import csw.messages.PubSub.PublisherMessage
import csw.messages._
import csw.messages.ccs.commands.{ControlCommand, Observe, Setup}
import csw.messages.framework.ComponentInfo
import csw.messages.location._
import csw.messages.params.states.CurrentState
import csw.services.location.scaladsl.LocationService
import csw.trombone.assembly.AssemblyCommandHandlerMsgs.CommandMessageE
import csw.trombone.assembly.AssemblyContext.{TromboneCalculationConfig, TromboneControlConfig}
import csw.trombone.assembly.CommonMsgs.UpdateHcdLocations
import csw.trombone.assembly.DiagPublisherMessages.{DiagnosticState, OperationsState}
import csw.trombone.assembly.ParamValidation._
import csw.trombone.assembly._

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

class TromboneAssemblyBehaviorFactory extends ComponentBehaviorFactory[DiagPublisherMessages] {
  override def handlers(
      ctx: ActorContext[ComponentMessage],
      componentInfo: ComponentInfo,
      pubSubRef: ActorRef[PublisherMessage[CurrentState]],
      locationService: LocationService
  ): ComponentHandlers[DiagPublisherMessages] =
    new TromboneAssemblyHandlers(ctx, componentInfo, pubSubRef, locationService)
}

class TromboneAssemblyHandlers(
    ctx: ActorContext[ComponentMessage],
    componentInfo: ComponentInfo,
    pubSubRef: ActorRef[PublisherMessage[CurrentState]],
    locationService: LocationService
) extends ComponentHandlers[DiagPublisherMessages](ctx, componentInfo, pubSubRef, locationService) {

  private var diagPublsher: ActorRef[DiagPublisherMessages] = _

  private var commandHandler: ActorRef[AssemblyCommandHandlerMsgs] = _

  implicit var ac: AssemblyContext  = _
  implicit val ec: ExecutionContext = ctx.executionContext

  private var runningHcds: Map[Connection, Option[ActorRef[SupervisorExternalMessage]]] = Map.empty

  def onRun(): Future[Unit] = Future.unit

  def initialize(): Future[Unit] = async {
    val (calculationConfig, controlConfig) = await(getAssemblyConfigs)
    ac = AssemblyContext(componentInfo.asInstanceOf[ComponentInfo], calculationConfig, controlConfig)

    val eventPublisher = ctx.spawnAnonymous(TrombonePublisher.make(ac))

    commandHandler =
      ctx.spawnAnonymous(new TromboneAssemblyCommandBehaviorFactory().make(ac, runningHcds, Some(eventPublisher)))

    diagPublsher = ctx.spawnAnonymous(DiagPublisher.make(ac, runningHcds.head._2, Some(eventPublisher)))
  }

  override def onShutdown(): Future[Unit] = {
    Future.successful(println("Received Shutdown"))
  }

  override def onGoOffline(): Unit = println("Received running offline")

  override def onGoOnline(): Unit = println("Received GoOnline")

  def onDomainMsg(mode: DiagPublisherMessages): Unit = mode match {
    case (DiagnosticState | OperationsState) => diagPublsher ! mode
    case _                                   ⇒
  }

  override def onSubmit(controlCommand: ControlCommand,
                        replyTo: ActorRef[CommandResponse]): CommandValidationResponse = {
    val validation = controlCommand match {
      case _: Setup   => validateOneSetup(controlCommand.asInstanceOf[Setup])
      case _: Observe => Accepted(controlCommand.runId)
    }
    if (validation == Accepted(controlCommand.runId)) {
      commandHandler ! CommandMessageE(Submit(controlCommand, replyTo))
    }
    validation
  }

  override def onOneway(controlCommand: ControlCommand): CommandValidationResponse = Accepted(controlCommand.runId)

  private def getAssemblyConfigs: Future[(TromboneCalculationConfig, TromboneControlConfig)] = ???

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    trackingEvent match {
      case LocationUpdated(location) =>
        runningHcds = runningHcds + (location.connection → Some(
          location.asInstanceOf[AkkaLocation].componentRef()
        ))
      case LocationRemoved(connection) =>
        runningHcds = runningHcds + (connection → None)
    }
    commandHandler ! UpdateHcdLocations(runningHcds)
  }
}
