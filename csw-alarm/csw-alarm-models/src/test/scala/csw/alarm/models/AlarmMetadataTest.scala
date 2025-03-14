/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.alarm.models
import csw.alarm.models.ActivationStatus.{Active, Inactive}
import csw.alarm.models.AlarmSeverity.*
import csw.alarm.models.Key.AlarmKey
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.NFIRAOS
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

// DEOPSCSW-439: Model to represent Alarm entity metadata
// CSW-83: Alarm models should take prefix
class AlarmMetadataTest extends AnyFunSuite with Matchers {

  val alarmMetadata = AlarmMetadata(
    prefix = Prefix(NFIRAOS, "trombone"),
    name = "tromboneAxisHighLimitAlarm",
    description = "Warns when trombone axis has reached the low limit",
    location = "south side",
    AlarmType.Absolute,
    Set(Warning, Major, Critical),
    probableCause = "the trombone software has failed or the stage was driven into the low limit",
    operatorResponse = "go to the NFIRAOS engineering user interface and select the datum axis command",
    isAutoAcknowledgeable = true,
    isLatchable = true,
    activationStatus = Active
  )

  // DEOPSCSW-435: Identify Alarm by Subsystem, component and AlarmName
  test("should get alarm key from AlarmMetadata | DEOPSCSW-439, DEOPSCSW-435") {
    alarmMetadata.alarmKey shouldBe AlarmKey(Prefix(NFIRAOS, "trombone"), "tromboneAxisHighLimitAlarm")
  }

  test("should always support Indeterminate and Okay severities  | DEOPSCSW-439") {
    alarmMetadata.allSupportedSeverities shouldEqual Set(Indeterminate, Okay, Warning, Major, Critical)
  }

  test("should tell if the alarm is active or not | DEOPSCSW-439") {
    alarmMetadata.isActive shouldBe true

    // for Inactive activation status
    alarmMetadata.copy(activationStatus = Inactive).isActive shouldBe false
  }

  test("should be able to get description of the alarm | DEOPSCSW-439") {
    alarmMetadata.description shouldBe "Warns when trombone axis has reached the low limit"
  }

  test("should be able to get alarm type | DEOPSCSW-439") {
    AlarmType.values should contain allElementsOf Set(alarmMetadata.alarmType)
  }

  // DEOPSCSW-453: Identify Probable cause of alarm
  test("should get probable cause from AlarmMetadata | DEOPSCSW-439, DEOPSCSW-453") {
    alarmMetadata.probableCause shouldBe "the trombone software has failed or the stage was driven into the low limit"
  }

  // DEOPSCSW-454: Instructions for corrective actions to handle the alarm
  test("should get operator response from AlarmMetadata | DEOPSCSW-439, DEOPSCSW-454") {
    alarmMetadata.operatorResponse shouldBe "go to the NFIRAOS engineering user interface and select the datum axis command"
  }

  // DEOPSCSW-455: Identify Alarm auto acknowledgement
  test("should be able to determine if alarm can be auto-acknowledged | DEOPSCSW-439, DEOPSCSW-455") {
    alarmMetadata.isAutoAcknowledgeable shouldBe true
  }

  // DEOPSCSW-456: Examine alarm latching state of each alarm
  test("should be able to determine if alarm can be latched | DEOPSCSW-439, DEOPSCSW-456") {
    alarmMetadata.isLatchable shouldBe true
  }
}
