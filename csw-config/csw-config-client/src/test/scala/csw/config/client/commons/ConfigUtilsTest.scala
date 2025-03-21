/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.config.client.commons
import java.io.File
import java.nio.file.{Files, Paths}
import org.apache.pekko.actor.typed
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import com.typesafe.config.{Config, ConfigFactory}
import csw.config.api.ConfigData
import csw.config.api.exceptions.{LocalFileNotFound, UnableToParseOptions}
import csw.config.api.scaladsl.ConfigClientService
import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.concurrent.duration.DurationDouble
import scala.concurrent.{Await, Future}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

class ConfigUtilsTest extends AnyFunSuite with Matchers with BeforeAndAfterEach with BeforeAndAfterAll with MockitoSugar {

  implicit val system: typed.ActorSystem[Nothing] = typed.ActorSystem(Behaviors.empty, "test")

  test("should throw exception if input file and default config is empty") {
    val mockedConfigClientService = mock[ConfigClientService]
    val configUtils               = new ConfigUtils(mockedConfigClientService)(system)

    val exception = intercept[UnableToParseOptions.type] {
      Await.result(configUtils.getConfig(isLocal = false, inputFilePath = None, defaultConfig = None), 7.seconds)
    }

    exception.getMessage shouldEqual "Could not parse command line options. See --help to know more."
  }

  test("should return default config if input file if not provided") {
    val mockedConfigClientService = mock[ConfigClientService]
    val configUtils               = new ConfigUtils(mockedConfigClientService)(system)
    val testConfig: Config        = system.settings.config

    val actualConfig =
      Await.result(configUtils.getConfig(isLocal = false, inputFilePath = None, defaultConfig = Some(testConfig)), 7.seconds)

    actualConfig shouldEqual testConfig
  }

  test("should use input file for config") {
    val mockedConfigClientService = mock[ConfigClientService]
    val configUtils               = new ConfigUtils(mockedConfigClientService)(system)
    val tmpFile                   = File.createTempFile("temp-config", ".conf")
    val tmpPath                   = tmpFile.toPath
    tmpFile.deleteOnExit()
    Files.write(tmpPath, "Name = Test".getBytes)

    val actualConfig =
      Await.result(configUtils.getConfig(isLocal = true, inputFilePath = Some(tmpPath), defaultConfig = None), 7.seconds)

    actualConfig shouldEqual ConfigFactory.parseFile(tmpFile)
  }

  test("should throw exception if input file does not exist") {
    val mockedConfigClientService = mock[ConfigClientService]
    val configUtils               = new ConfigUtils(mockedConfigClientService)(system)
    val invalidFilePath           = Paths.get("/invalidPath.conf")

    val exception = intercept[LocalFileNotFound] {
      Await.result(configUtils.getConfig(isLocal = true, inputFilePath = Some(invalidFilePath), defaultConfig = None), 7.seconds)
    }

    exception.getMessage shouldEqual s"File does not exist on local disk at path ${invalidFilePath.toString}"
  }

  test("should get config from remote input file") {
    val mockedConfigClientService = mock[ConfigClientService]
    val configUtils               = new ConfigUtils(mockedConfigClientService)(system)
    val remoteFilePath            = Paths.get("remoteFile.conf")
    val configValue1: String =
      """
        |axisName1 = tromboneAxis1
        |axisName2 = tromboneAxis2
        |axisName3 = tromboneAxis3
        |""".stripMargin
    val expectedConfigData = ConfigData.fromString(configValue1)
    val expectedConfig     = Await.result(expectedConfigData.toConfigObject, 7.seconds)

    when(mockedConfigClientService.getActive(remoteFilePath))
      .thenReturn(Future.successful(Some(expectedConfigData)))

    val actualConfig =
      Await.result(configUtils.getConfig(isLocal = false, inputFilePath = Some(remoteFilePath), defaultConfig = None), 7.seconds)

    actualConfig shouldEqual expectedConfig
  }
}
