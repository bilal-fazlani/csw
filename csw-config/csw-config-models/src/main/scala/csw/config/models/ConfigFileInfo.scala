/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.config.models

import java.nio.file.Path

/**
 * Contains information about a config file stored in the config service
 *
 * @param path the path of file sitting in config service
 * @param id the ConfigId representing unique id of the file
 * @param comment the comment end user wants to provide while committing the file in config service
 */
case class ConfigFileInfo private[csw] (path: Path, id: ConfigId, author: String, comment: String)
