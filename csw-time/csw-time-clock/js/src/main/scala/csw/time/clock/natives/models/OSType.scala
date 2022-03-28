/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.time.clock.natives.models

private[time] sealed trait OSType

private[time] object OSType {
  case object Linux extends OSType
  case object Other extends OSType

  val value: OSType = Other
}
