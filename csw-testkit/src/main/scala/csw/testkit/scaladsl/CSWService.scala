/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.testkit.scaladsl

sealed trait CSWService extends Product with Serializable

/**
 * Supported services by framework testkit.
 *
 * Specify one or more services from following ADT's while creating FrameworkTestKit
 * and testkit will make sure that those services are started.
 *
 * Example:
 * == With Scalatest Integration ==
 * {{{
 *   class ScalaTestFrameworkTestKitSpec
 *     extends ScalaTestFrameworkTestKit(LocationServer, ConfigServer, EventServer)
 *     with FunSuiteLike
 * }}}
 *
 * == With FrameworkTestKit ==
 * {{{
 *
 *   private val frameworkTestKit = FrameworkTestKit()
 *   frameworkTestKit.start(ConfigServer, EventServer)
 *
 * }}}
 */
object CSWService {
  case object LocationServer         extends CSWService
  case object LocationServerWithAuth extends CSWService
  case object ConfigServer           extends CSWService
  case object EventServer            extends CSWService
  case object AlarmServer            extends CSWService
  case object DatabaseServer         extends CSWService
}
