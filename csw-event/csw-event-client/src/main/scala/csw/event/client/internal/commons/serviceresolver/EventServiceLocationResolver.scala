/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.event.client.internal.commons.serviceresolver

import java.net.URI

import csw.event.client.internal.commons.EventServiceConnection
import csw.location.api.scaladsl.LocationService

import cps.compat.FutureAsync.*
import scala.concurrent.duration.DurationDouble
import scala.concurrent.{ExecutionContext, Future}

/**
 * Provides the connection information of `Event Service` by resolving the location through `Location Service`
 */
private[event] class EventServiceLocationResolver(locationService: LocationService)(implicit ec: ExecutionContext)
    extends EventServiceResolver {

  def uri(): Future[URI] =
    async {
      val location = await(locationService.resolve(EventServiceConnection.value, 5.seconds)).getOrElse(
        throw new RuntimeException(
          s"event service connection=${EventServiceConnection.value.name} can not be resolved"
        )
      )
      location.uri
    }
}
