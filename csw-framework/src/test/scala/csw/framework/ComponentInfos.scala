/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.framework

import csw.command.client.models.framework.ComponentInfo
import csw.command.client.models.framework.LocationServiceUsage.{DoNotRegister, RegisterOnly}
import csw.framework.models.ContainerInfo
import csw.location.api.models.ComponentType.{Assembly, HCD}
import csw.prefix.models.Prefix

import scala.concurrent.duration.DurationDouble

object ComponentInfos {
  val assemblyInfo: ComponentInfo = ComponentInfo(
    Prefix("WFOS.SampleAssembly"),
    Assembly,
    "csw.common.components.framework.SampleComponentHandlers",
    DoNotRegister,
    Set.empty
  )

  val assemblyInfoToSimulateFailure: ComponentInfo = ComponentInfo(
    Prefix("WFOS.Trombone"),
    Assembly,
    "csw.common.components.framework.ComponentHandlerToSimulateFailure",
    DoNotRegister,
    Set.empty
  )

  val hcdInfo: ComponentInfo = ComponentInfo(
    Prefix("WFOS.SampleHcd"),
    HCD,
    "csw.common.components.framework.SampleComponentHandlers",
    RegisterOnly,
    Set.empty
  )

  val hcdInfoWithInitializeTimeout: ComponentInfo = ComponentInfo(
    Prefix("WFOS.SampleHcd"),
    HCD,
    "csw.common.components.framework.SampleComponentHandlers",
    RegisterOnly,
    Set.empty,
    50.millis
  )

  val hcdInfoWithRunTimeout: ComponentInfo = ComponentInfo(
    Prefix("WFOS.SampleHcd"),
    HCD,
    "csw.common.components.framework.SampleComponentHandlers",
    RegisterOnly,
    Set.empty,
    5.seconds
  )

  val hcdInfoWithHandlerException: ComponentInfo = ComponentInfo(
    Prefix("WFOS.SampleHcd"),
    HCD,
    "csw.common.components.framework.InvalidComponentHandlers",
    RegisterOnly,
    Set.empty,
    5.seconds
  )

  val jHcdInfoWithHandlerException: ComponentInfo = ComponentInfo(
    Prefix("WFOS.SampleHcd"),
    HCD,
    "csw.framework.javadsl.components.JInvalidComponentHandlers",
    RegisterOnly,
    Set.empty,
    5.seconds
  )

  val dummyInfo: ComponentInfo = ComponentInfo(Prefix("WFOS.dummyhcd"), HCD, "dummy", DoNotRegister)

  val containerInfo: ContainerInfo = ContainerInfo("container", Set(hcdInfo, assemblyInfo))
}
