/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.alarm.client.internal.models

import csw.alarm.models.Key.AlarmKey
import csw.alarm.models._

private[alarm] case class Alarm(key: AlarmKey, metadata: AlarmMetadata, status: AlarmStatus, severity: FullAlarmSeverity)
