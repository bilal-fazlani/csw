/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.database.exceptions

/**
 * Represents the exception while connecting to database server e.g. in case of providing incorrect username or password
 *
 * @param msg represents a brief description of the exception from the underlying or root exception
 * @param cause represents the underlying or root exception
 */
case class DatabaseException(msg: String, cause: Throwable) extends RuntimeException(msg, cause)
