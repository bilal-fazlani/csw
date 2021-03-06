package csw.framework.scaladsl

import akka.actor.typed.ActorRef
import csw.location.api.AkkaRegistrationFactory
import csw.location.api.extensions.ActorExtension.RichActor
import csw.location.api.models.AkkaRegistration
import csw.location.api.models.Connection.AkkaConnection

/**
 * `RegistrationFactory` helps creating an AkkaRegistration. It is currently used by `csw-framework` to register different components on jvm boot-up.
 */
class RegistrationFactory {

  /**
   * Creates an AkkaRegistration from provided parameters. Currently, it is used to register components except Container.
   * A [[csw.location.api.exceptions.LocalAkkaActorRegistrationNotAllowed]] can be thrown if the actorRef provided
   * is not a remote actorRef.
   *
   * @param akkaConnection the AkkaConnection representing the component
   * @param actorRef the supervisor actorRef of the component
   * @return a handle to the AkkaRegistration that is used to register in location service
   */
  def akkaTyped(akkaConnection: AkkaConnection, actorRef: ActorRef[_]): AkkaRegistration =
    AkkaRegistrationFactory.make(akkaConnection, actorRef.toURI)

}
