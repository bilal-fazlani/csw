/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.config.models

/**
 * Type of an id returned from ConfigManager create or update methods
 *
 * @param id the string representation of the unique id for the file
 */
case class ConfigId(id: String)

object ConfigId {
  def apply(id: Long): ConfigId = ConfigId(id.toString)
}
