/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.event.cli.utils

import csw.params.core.generics.Parameter

case class PathSelector(paths: List[String]) {
  def transform(paramSet: Set[Parameter[?]]): Set[Parameter[?]] = {
    paths match {
      case ::(head, next) => paramSet.filter(x => paths.contains(x.keyName))
      case Nil            => paramSet
    }
  }
}
