/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.alarm.api.scaladsl
import csw.alarm.api.internal.{HealthService, MetadataService, SeverityService, StatusService}

/**
 * An AlarmAdminService interface to update and query alarms. All operations are non-blocking.
 */
trait AlarmAdminService extends SeverityService with MetadataService with HealthService with StatusService
