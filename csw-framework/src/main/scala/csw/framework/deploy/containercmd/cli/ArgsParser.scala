/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.framework.deploy.containercmd.cli

import java.nio.file.Paths

import csw.framework.BuildInfo
import scopt.OptionParser

/**
 * Parses the command line options using `scopt` library.
 */
private[containercmd] class ArgsParser(name: String) {

  val parser: OptionParser[Options] = new scopt.OptionParser[Options](name) {
    head(name, BuildInfo.version)

    opt[Unit]("local") action { (_, c) =>
      c.copy(local = true)
    } text "Optional: if provided then run using the file on local file system else fetch it from config service"

    arg[String]("<file>").required() action { (x, c) =>
      c.copy(inputFilePath = Some(Paths.get(x)))
    } text "specifies config file path which gets fetched from config service or local file system based on other options"

    help("help")
    version("version")
  }

  def parse(args: Seq[String]): Option[Options] = parser.parse(args, Options())
}
