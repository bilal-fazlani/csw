/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.command.client.models.framework

import csw.serializable.CommandSerializable

/**
 * Represents a collection of components created in a single container
 *
 * @param components a set of components with its supervisor and componentInfo
 */
case class Components(components: Set[Component]) extends CommandSerializable
