package csw.framework.internal.container

import akka.typed.Behavior
import akka.typed.scaladsl.Actor
import akka.typed.scaladsl.Actor.MutableBehavior
import csw.framework.internal.supervisor.SupervisorInfoFactory
import csw.framework.models.ContainerInfo
import csw.messages.ContainerMessage
import csw.services.location.scaladsl.{LocationService, RegistrationFactory}

/**
 * Factory for creating [[MutableBehavior]] of a container component
 */
object ContainerBehaviorFactory {
  def behavior(
      containerInfo: ContainerInfo,
      locationService: LocationService,
      registrationFactory: RegistrationFactory
  ): Behavior[ContainerMessage] = {
    val supervisorFactory = new SupervisorInfoFactory(containerInfo.name)
    Actor.mutable(
      ctx ⇒ new ContainerBehavior(ctx, containerInfo, supervisorFactory, registrationFactory, locationService)
    )
  }
}
