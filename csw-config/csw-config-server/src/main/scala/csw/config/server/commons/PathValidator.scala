/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.config.server.commons

import java.util.regex.Pattern

/**
 * PathValidator is used to validate any requested path against invalid characters at an entry point of config service
 */
object PathValidator {

  private val invalidChars   = "!#<>$%&'@^`~+,;=\\s"
  private val invalidPattern = Pattern.compile(s"[$invalidChars]+")

  private val invalidCharsMessage: String = invalidChars
    .replace("\\s", " ")
    .map(char => s"{$char}")
    .mkString(",")

  /**
   * gets a message for presence of invalid characters in the file path
   *
   * @param path string representation of path
   * @return message for presence of invalid characters
   */
  def message(path: String): String =
    s"Input file path '$path' contains invalid characters. Note, these characters $invalidCharsMessage are not allowed in file path"

  /**
   * validates string representation of path for the presence of unsupported characters in file path
   *
   * @param path string representation of path
   * @return true if the path does not contain any unsupported character, false otherwise
   */
  def isValid(path: String): Boolean = !invalidPattern.matcher(path).find()
}
