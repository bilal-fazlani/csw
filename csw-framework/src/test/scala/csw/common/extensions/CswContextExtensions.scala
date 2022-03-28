/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.common.extensions
import csw.framework.models.CswContext
import csw.command.client.models.framework.ComponentInfo

object CswContextExtensions {
  implicit class RichCswContext(val cswCtx: CswContext) extends AnyVal {
    def copy(newComponentInfo: ComponentInfo): CswContext =
      new CswContext(
        cswCtx.locationService,
        cswCtx.eventService,
        cswCtx.alarmService,
        cswCtx.timeServiceScheduler,
        cswCtx.loggerFactory,
        cswCtx.configClientService,
        cswCtx.currentStatePublisher,
        cswCtx.commandResponseManager,
        newComponentInfo
      )
  }
}
