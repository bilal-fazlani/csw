/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.time.scheduler.api

/**
 * API for a scheduled periodic task, that allows it to be cancelled.
 */
trait Cancellable {

  /**
   * Cancels this Cancellable and returns true if that was successful.
   * If this cancellable was (concurrently) cancelled already, then this method
   * will return false.
   *
   * @return whether or not the cancellable was cancelled successfully
   */
  def cancel(): Boolean
}
