/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.config.api.scaladsl

import java.nio.file.Path

import csw.config.api.ConfigData
import csw.config.models.ConfigId

import scala.concurrent.Future

/**
 * Defines an interface to be used by clients for retrieving configuration information
 */
trait ConfigClientService {

  /**
   * Returns true if the given path exists and is being managed
   *
   * @param path the file path relative to the repository root
   * @param id   revision of the file
   * @return a future that completes with true if the file exists, false otherwise. It can fail with
   *         [[csw.config.api.exceptions.InvalidInput]] or [[csw.config.api.exceptions.FileNotFound]]
   */
  def exists(path: Path, id: Option[ConfigId] = None): Future[Boolean]

  /**
   * Gets and returns the content of active version of the file stored under the given path.
   *
   * @param path the file path relative to the repository root
   * @return a future object that can be used to access the file's data, if found or fails with an
   *         [[csw.config.api.exceptions.EmptyResponse]] or [[csw.config.api.exceptions.InvalidInput]]
   *         or [[csw.config.api.exceptions.FileNotFound]]
   */
  def getActive(path: Path): Future[Option[ConfigData]]
}
