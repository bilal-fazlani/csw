/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.logging.macros

private[logging] object DefaultSourceLocation extends SourceLocation("", "", "", -1)

/**
 * A position in a Scala source file
 *
 * @param fileName  the name of the file
 * @param packageName  the package
 * @param className  the name of the enclosing class
 * @param line  a line number
 */
case class SourceLocation(fileName: String, packageName: String, className: String, line: Int)
