/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.params.core.generics

import java.util
import java.util.Optional

import csw.params.core.models.Units
import csw.params.extensions.OptionConverters.RichOption
import io.bullet.borer.derivation.key

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/**
 * Parameter represents a KeyName, KeyType, array of values and units applicable to values. Parameter sits as payload for
 * sending commands and events between components.
 *
 * @param keyName the name of the key
 * @param keyType reference to an object of type KeyType[S]
 * @param items an Array of values of type S
 * @param units applicable units
 * @tparam S the type of items this parameter holds
 */
case class Parameter[S](
    keyName: String,
    keyType: KeyType[S],
    @key("values") items: mutable.ArraySeq[S],
    units: Units
) {

  /**
   * An Array of values this parameter holds
   */
  def values: Array[S] = items.array.asInstanceOf[Array[S]]

  /**
   * A Java helper that returns a List of values this parameter holds
   */
  def jValues: util.List[S] = items.asJava

  /**
   * The number of values in this parameter (values.size)
   *
   * @return length of the array of items
   */
  def size: Int = items.size

  /**
   * Returns the value at the given index, throwing an exception if the index is out of range
   *
   * @param index the index of a value
   * @return the value at the given index (may throw an exception if the index is out of range)
   */
  def apply(index: Int): S = value(index)

  /**
   * Returns the value at the given index, throwing an exception if the index is out of range
   * This is a Scala convenience method
   *
   * @param index the index of a value
   * @return the value at the given index (may throw an exception if the index is out of range)
   */
  def value(index: Int): S = items(index)

  /**
   * Get method returns an option of value if present at the given index else none
   *
   * @param index the index of a value
   * @return some value at the given index as an Option, if the index is in range, otherwise None
   */
  def get(index: Int): Option[S] = items.lift(index)

  /**
   * A Java helper that returns an option of value if present at the given index else empty
   *
   * @param index the index of a value
   * @return value at the given index as an Optional, if the index is in range, otherwise empty
   */
  def jGet(index: Int): Optional[S] = items.lift(index).asJava

  /**
   * Returns the first value as a convenience when storing a single value
   *
   * @return the first or default value (Use this if you know there is only a single value)
   */
  def head: S = value(0)

  /**
   * Sets the units for the values
   *
   * @param unitsIn the units for the values
   * @return a new instance of this parameter with the units set
   */
  def withUnits(unitsIn: Units): Parameter[S] = copy(units = unitsIn)

  /**
   * Returns a formatted string representation with a KeyName
   */
  override def toString: String = s"$keyName($valuesToString$units)"

  /**
   * A comma separated string representation of all values this parameter holds
   */
  def valuesToString: String = items.mkString("(", ",", ")")
}
