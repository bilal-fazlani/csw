/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.alarm.models

import csw.alarm.models.Key.AlarmKey

sealed trait AutoRefreshSeverityMessage {
  def key: AlarmKey
}

object AutoRefreshSeverityMessage {
  case class AutoRefreshSeverity(key: AlarmKey, severity: AlarmSeverity)        extends AutoRefreshSeverityMessage
  private[alarm] case class SetSeverity(key: AlarmKey, severity: AlarmSeverity) extends AutoRefreshSeverityMessage
  case class CancelAutoRefresh(key: AlarmKey)                                   extends AutoRefreshSeverityMessage
}
