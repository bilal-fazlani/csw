package csw.trombone.hcd

import java.io.File

import csw.param.commands.{CommandInfo, Setup}
import csw.param.states.CurrentState
import csw.param.models.{Choice, Prefix}
import csw.param.generics.KeyType
import csw.param.generics.KeyType.ChoiceKey
import csw.units.Units.encoder

object TromboneHcdState {
  val tromboneConfigFile = new File("trombone/tromboneHCD.conf")
  val resource           = new File("tromboneHCD.conf")

  // HCD Info
  val componentName = "lgsTromboneHCD"
//  val componentType = ComponentType.HCD
  val componentClassName = "csw.examples.vslice.hcd.TromboneHCD"
  val trombonePrefix     = "nfiraos.ncc.tromboneHCD"

  val tromboneAxisName = "tromboneAxis"

  val axisStatePrefix             = s"$trombonePrefix.axis1State"
  val axisStateCK: Prefix         = Prefix(axisStatePrefix)
  val axisNameKey                 = KeyType.StringKey.make("axisName")
  val AXIS_IDLE                   = Choice(AxisState.AXIS_IDLE.toString)
  val AXIS_MOVING                 = Choice(AxisState.AXIS_MOVING.toString)
  val AXIS_ERROR                  = Choice(AxisState.AXIS_ERROR.toString)
  val stateKey                    = ChoiceKey.make("axisState", AXIS_IDLE, AXIS_MOVING, AXIS_ERROR)
  val positionKey                 = KeyType.IntKey.make("position")
  val positionUnits: encoder.type = encoder
  val inLowLimitKey               = KeyType.BooleanKey.make("lowLimit")
  val inHighLimitKey              = KeyType.BooleanKey.make("highLimit")
  val inHomeKey                   = KeyType.BooleanKey.make("homed")

  val defaultAxisState: CurrentState = CurrentState(axisStateCK).madd(
    axisNameKey    -> tromboneAxisName,
    stateKey       -> AXIS_IDLE,
    positionKey    -> 0 withUnits encoder,
    inLowLimitKey  -> false,
    inHighLimitKey -> false,
    inHomeKey      -> false
  )

  val axisStatsPrefix     = s"$trombonePrefix.axisStats"
  val axisStatsCK: Prefix = Prefix(axisStatsPrefix)
  val datumCountKey       = KeyType.IntKey.make("initCount")
  val moveCountKey        = KeyType.IntKey.make("moveCount")
  val homeCountKey        = KeyType.IntKey.make("homeCount")
  val limitCountKey       = KeyType.IntKey.make("limitCount")
  val successCountKey     = KeyType.IntKey.make("successCount")
  val failureCountKey     = KeyType.IntKey.make("failureCount")
  val cancelCountKey      = KeyType.IntKey.make("cancelCount")
  val defaultStatsState: CurrentState = CurrentState(axisStatsCK).madd(
    axisNameKey     -> tromboneAxisName,
    datumCountKey   -> 0,
    moveCountKey    -> 0,
    homeCountKey    -> 0,
    limitCountKey   -> 0,
    successCountKey -> 0,
    failureCountKey -> 0,
    cancelCountKey  -> 0
  )

  val axisConfigPrefix     = s"$trombonePrefix.axisConfig"
  val axisConfigCK: Prefix = Prefix(axisConfigPrefix)
  // axisNameKey
  val lowLimitKey    = KeyType.IntKey.make("lowLimit")
  val lowUserKey     = KeyType.IntKey.make("lowUser")
  val highUserKey    = KeyType.IntKey.make("highUser")
  val highLimitKey   = KeyType.IntKey.make("highLimit")
  val homeValueKey   = KeyType.IntKey.make("homeValue")
  val startValueKey  = KeyType.IntKey.make("startValue")
  val stepDelayMSKey = KeyType.IntKey.make("stepDelayMS")
  // No full default current state because it is determined at runtime
  val defaultConfigState: CurrentState = CurrentState(axisConfigCK).madd(
    axisNameKey -> tromboneAxisName
  )

  val axisMovePrefix     = s"$trombonePrefix.move"
  val axisMoveCK: Prefix = Prefix(axisMovePrefix)

  def positionSC(commandInfo: CommandInfo, value: Int): Setup =
    Setup(commandInfo, axisMoveCK).add(positionKey -> value withUnits encoder)

  val axisDatumPrefix                   = s"$trombonePrefix.datum"
  val axisDatumCK: Prefix               = Prefix(axisDatumPrefix)
  def datumSC(commandInfo: CommandInfo) = Setup(commandInfo, axisDatumCK)

  val axisHomePrefix                   = s"$trombonePrefix.home"
  val axisHomeCK: Prefix               = Prefix(axisHomePrefix)
  def homeSC(commandInfo: CommandInfo) = Setup(commandInfo, axisHomeCK)

  val axisCancelPrefix                   = s"$trombonePrefix.cancel"
  val axisCancelCK: Prefix               = Prefix(axisCancelPrefix)
  def cancelSC(commandInfo: CommandInfo) = Setup(commandInfo, axisCancelCK)
}
