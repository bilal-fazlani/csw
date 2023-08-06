/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.integtration.common

import scala.concurrent.duration.DurationLong
import scala.concurrent.{Await, Future}

object TestFutureExtension {

  class RichFuture[T](val f: Future[T]) extends AnyVal {
    def await: T = Await.result(f, 100.seconds)
  }

  given futureConversion[T]: Conversion[Future[T], RichFuture[T]] with
    def apply(x: Future[T]): RichFuture[T] = RichFuture(x)
}
