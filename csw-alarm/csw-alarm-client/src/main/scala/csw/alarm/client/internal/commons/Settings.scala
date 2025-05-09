/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.alarm.client.internal.commons
import com.typesafe.config.Config

import scala.jdk.DurationConverters.*
import scala.concurrent.duration.FiniteDuration

private[alarm] class Settings(config: Config) {

  private val alarmConfig = config.getConfig("csw-alarm")

  val masterId: String                = alarmConfig.getString("redis.masterId")
  val refreshInterval: FiniteDuration = alarmConfig.getDuration("refresh-interval").toScala // default value is 3 seconds
  val maxMissedRefreshCounts: Int     = alarmConfig.getInt("max-missed-refresh-counts")     // default value is 3 times
  val shelveTimeout: String           = alarmConfig.getString("shelve-timeout")
  val severityTTLInSeconds: Long      = refreshInterval.toSeconds * maxMissedRefreshCounts

}
