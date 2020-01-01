package csw.prefix.models

/**
 * A top level key for a parameter set: combination of subsystem and the subsystem's prefix
 * e.g. tcs.filter.wheel, wfos.prog.cloudcover, etc
 *
 * @note Component name should not contain
 *  - leading or trailing spaces
 *  - and hyphen (-)
 * @param subsystem     component subsystem - tcs (TCS), wfos (WFOS)
 * @param componentName component name - filter.wheel, prog.cloudcover. It is changed to lowercase while constructing the Prefix.
 */
case class Prefix private (subsystem: Subsystem, componentName: String) {
  require(componentName == componentName.trim, "component name has leading and trailing whitespaces")
  require(!componentName.contains("-"), "component name has '-'")

  /**
   * String representation of prefix e.g. tcs.filter.wheel where tcs is the subsystem name and filter.wheel is the component name
   */
  val value: String = s"${subsystem.name}${Prefix.SEPARATOR}$componentName".toLowerCase

  /**
   * String representation of prefix e.g. tcs.filter.wheel where tcs is the subsystem name and filter.wheel is the component name
   */
  override def toString: String = value
}

object Prefix {
  private val SEPARATOR = "."

  /**
   * Creates a Prefix based on the given value of format tcs.filter.wheel and splits it to have tcs as `subsystem` and filter.wheel
   * as `componentName`
   *
   * @param value of format tcs.filter.wheel
   * @return a Prefix instance
   */
  def apply(value: String): Prefix = {
    require(value.contains(SEPARATOR), s"prefix must have a '$SEPARATOR' separator")
    val Array(subsystem, componentName) = value.split(s"\\$SEPARATOR", 2)
    Prefix(Subsystem.withNameInsensitive(subsystem), componentName)
  }

  /**
   * A top level key for a parameter set: combination of subsystem and the subsystem's prefix
   * e.g. tcs.filter.wheel, wfos.prog.cloudcover, etc
   *
   * @note Component name should not contain
   *  - leading or trailing spaces
   *  - and hyphen (-)
   * @param subsystem     component subsystem - tcs (TCS), wfos (WFOS)
   * @param componentName component name - filter.wheel, prog.cloudcover. It is changed to lowercase while constructing the Prefix.
   */
  def apply(subsystem: Subsystem, componentName: String): Prefix = new Prefix(subsystem, componentName.toLowerCase)
}