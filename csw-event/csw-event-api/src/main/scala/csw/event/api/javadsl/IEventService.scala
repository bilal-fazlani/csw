/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.event.api.javadsl

import csw.event.api.scaladsl.EventService

/**
 * An interface to provide access to [[csw.event.api.javadsl.IEventPublisher]] and [[csw.event.api.javadsl.IEventSubscriber]].
 */
trait IEventService {

  /**
   * A default instance of [[csw.event.api.javadsl.IEventPublisher]].
   * This could be shared across under normal operating conditions to share the underlying connection to event server.
   */
  lazy val defaultPublisher: IEventPublisher = makeNewPublisher()

  /**
   * A default instance of [[csw.event.api.javadsl.IEventSubscriber]].
   * This could be shared across under normal operating conditions to share the underlying connection to event server.
   */
  lazy val defaultSubscriber: IEventSubscriber = makeNewSubscriber()

  /**
   * Create a new instance of [[csw.event.api.javadsl.IEventPublisher]] with a separate underlying connection than the default instance.
   * The new instance will be required when the location of Event Service is updated or in case the performance requirements
   * of a publish operation demands a separate connection to be used.
   *
   * @return new instance of [[csw.event.api.javadsl.IEventPublisher]]
   */
  def makeNewPublisher(): IEventPublisher

  /**
   * Create a new instance of [[csw.event.api.javadsl.IEventPublisher]] with a separate underlying connection than the default instance.
   * The new instance will be required when the location of Event Service is updated or in case the performance requirements
   * of a subscribe operation demands a separate connection to be used.
   *
   * @return new instance of [[csw.event.api.javadsl.IEventSubscriber]]
   */
  def makeNewSubscriber(): IEventSubscriber

  /**
   * Returns the Scala API for this instance of event service
   */
  def asScala: EventService
}
