/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.location.server.cli

import csw.location.api.models.NetworkType
import csw.network.utils.Networks

case class Options(
    clusterPort: Option[Int] = None,
    outsideNetwork: Boolean = false
) {
  val httpBindHost: String = if (outsideNetwork) Networks(NetworkType.Outside.envKey).hostname else "127.0.0.1"
}
