/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.time.core.models.utils

import csw.time.clock.natives.models.OSType

trait TestProperties {
  def precision: Int
}

object JTestProperties {
  class LinuxProperties extends TestProperties {
    override val precision: Int = 9
  }

  class NonLinuxProperties extends TestProperties {
    override val precision: Int = 6
  }

  val instance: TestProperties = OSType.value match {
    case OSType.Linux => new LinuxProperties
    case OSType.Other => new NonLinuxProperties
  }
}
