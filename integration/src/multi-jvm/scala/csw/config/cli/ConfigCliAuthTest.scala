/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.config.cli

import java.io.ByteArrayInputStream
import java.nio.file.Paths
import org.apache.pekko.actor.typed.scaladsl.adapter.*
import org.apache.pekko.remote.testconductor.RoleName
import com.typesafe.config.ConfigFactory
import csw.aas.core.deployment.AuthServiceLocation
import csw.commons.ResourceReader
import csw.config.cli.args.Options
import csw.config.cli.wiring.Wiring
import csw.config.client.scaladsl.ConfigClientFactory
import csw.config.server.commons.TestFileUtils
import csw.config.server.{ServerWiring, Settings}
import csw.location.helpers.{LSNodeSpec, NMembersAndSeed}
import csw.location.server.http.MultiNodeHTTPLocationService
import org.scalatest.Ignore
import org.scalatest.funsuite.AnyFunSuiteLike
import org.tmt.embedded_keycloak.KeycloakData.*
import org.tmt.embedded_keycloak.{EmbeddedKeycloak, KeycloakData, Settings as KeycloakSettings}

import scala.concurrent.Await
import scala.concurrent.duration.{DurationLong, FiniteDuration}

class MultiNodeTestConfig extends NMembersAndSeed(2) {
  val keycloak: RoleName = seed
  val (configServer, client) = members match {
    case Vector(configServer, client) => (configServer, client)
    case x                            => throw new MatchError(x)
  }

}

// XXX TODO: after keycloak upgrade, console login option is no longer available

@Ignore
class ConfigCliAuthTestMultiJvmNode1 extends ConfigCliAuthTest(0)

@Ignore
class ConfigCliAuthTestMultiJvmNode2 extends ConfigCliAuthTest(0)

@Ignore
class ConfigCliAuthTestMultiJvmNode3 extends ConfigCliAuthTest(0)

// DEOPSCSW-43: Access Configuration service from any CSW component
@Ignore
class ConfigCliAuthTest(ignore: Int)
    extends LSNodeSpec(config = new MultiNodeTestConfig, mode = "http")
    with MultiNodeHTTPLocationService
    with AnyFunSuiteLike {

  import config._
  import system.dispatcher

  private val adminUser     = "config-user1"
  private val adminPassword = "config-user1"

  private val testFileUtils = new TestFileUtils(new Settings(ConfigFactory.load()))

  private val defaultTimeout: FiniteDuration = 10.seconds
  private val serverTimeout: FiniteDuration  = 30.minutes

  override def afterAll(): Unit = {
    super.afterAll()
    testFileUtils.deleteServerFiles()
  }

  test(s"${testPrefix} should upload, update, get and set active version of configuration files | DEOPSCSW-43, CSW-106") {
    runOn(keycloak) {
      val configAdmin = "config-admin"

      val `csw-config-cli` = Client("tmt-frontend-app", "public", passwordGrantEnabled = false, authorizationEnabled = false)

      val keycloakData = KeycloakData(
        realms = Set(
          Realm(
            "TMT",
            clients = Set(`csw-config-cli`),
            users = Set(
              ApplicationUser(
                adminUser,
                adminPassword,
                firstName = adminUser,
                lastName = adminUser,
                email = s"$adminUser@tmt.org",
                realmRoles = Set(configAdmin)
              )
            ),
            realmRoles = Set(configAdmin)
          )
        )
      )
      val embeddedKeycloak = new EmbeddedKeycloak(keycloakData, KeycloakSettings(printProcessLogs = false))
      val stopHandle       = Await.result(embeddedKeycloak.startServer(), serverTimeout)
      Await.result(new AuthServiceLocation(locationService).register(KeycloakSettings.default.port), defaultTimeout)
      enterBarrier("keycloak started")
      enterBarrier("config-server-started")
      enterBarrier("test-finished")
      stopHandle.stop()
    }

    runOn(configServer) {
      enterBarrier("keycloak started")
      val serverWiring = ServerWiring.make(
        ConfigFactory.parseString(
          "auth-config.client-id = tmt-backend-app\n" +
            "auth-config.realm=TMT"
        )
      )
      serverWiring.svnRepo.initSvnRepo()
      serverWiring.httpService.registeredLazyBinding.await
      enterBarrier("config-server-started")
      enterBarrier("test-finished")
    }

    runOn(client) {
      val (filePath, fileContents) = ResourceReader.readAndCopyToTmp("/tromboneHCDContainer.conf")
      val repoPath1                = Paths.get("/client1/hcd/text/tromboneHCDContainer.conf")

      enterBarrier("keycloak started")
      enterBarrier("config-server-started")

      val wiring = Wiring.noPrinting(
        ConfigFactory.parseString(
          "auth-config.client-id = tmt-frontend-app\n" +
            "auth-config.realm=TMT"
        )
      )
      val runner = wiring.commandLineRunner

      val stream = new ByteArrayInputStream(s"$adminUser\n$adminPassword".getBytes())

      val stdIn = System.in
      try {
        System.setIn(stream)
        // XXX TODO: after keycloak upgrade, console login option is no longer available
//        runner.login(Options(console = true))
        runner.login(Options())
      }
      finally {
        System.setIn(stdIn)
      }

      runner.create(
        Options(relativeRepoPath = Some(repoPath1), inputFilePath = Some(filePath), comment = Some("test"))
      )

      val configService     = ConfigClientFactory.clientApi(system.toTyped, locationService)
      val actualConfigValue = configService.getActive(repoPath1).await.get.toStringF.await
      actualConfigValue shouldBe fileContents
      enterBarrier("test-finished")
    }
    enterBarrier("end")
  }
}
