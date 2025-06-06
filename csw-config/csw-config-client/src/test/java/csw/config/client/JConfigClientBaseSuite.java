/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.config.client;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.SpawnProtocol;
import csw.config.api.javadsl.IConfigClientService;
import csw.config.api.javadsl.IConfigService;
import csw.config.client.internal.ActorRuntime;
import csw.config.client.javadsl.JConfigClientFactory;
import csw.config.server.ServerWiring;
import csw.config.server.ServerWiring$;
import csw.config.server.commons.TestFileUtils;
import csw.config.server.http.HttpService;
import csw.config.server.mocks.JMockedAuthentication;
import csw.location.api.javadsl.ILocationService;
import csw.location.client.javadsl.JHttpLocationServiceFactory;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

public class JConfigClientBaseSuite extends JMockedAuthentication {

    private final csw.location.server.internal.ServerWiring locationWiring = new csw.location.server.internal.ServerWiring(false);
    public final ActorSystem<SpawnProtocol.Command> system = ActorSystem.create(SpawnProtocol.create(), "Guardian");
    private final ActorRuntime actorRuntime = new ActorRuntime(system);
    private final ILocationService clientLocationService = JHttpLocationServiceFactory.makeLocalClient(actorRuntime.actorSystem());

    public final IConfigService configService = JConfigClientFactory.adminApi(actorRuntime.actorSystem(), clientLocationService, factory());
    public final IConfigClientService configClientApi = JConfigClientFactory.clientApi(actorRuntime.actorSystem(), clientLocationService);

    private final ServerWiring serverWiring = ServerWiring$.MODULE$.make(securityDirectives());
    private final HttpService httpService = serverWiring.httpService();
    private final TestFileUtils testFileUtils = new TestFileUtils(serverWiring.settings());
    private final FiniteDuration timeout = Duration.create(10, "seconds");

    public void setup() throws Exception {
        Await.result(locationWiring.locationHttpService().start("127.0.0.1"), timeout);
        Await.result(httpService.registeredLazyBinding(), timeout);
    }

    public void initSvnRepo() {
        serverWiring.svnRepo().initSvnRepo();
    }

    public void deleteServerFiles() {
        testFileUtils.deleteServerFiles();
    }

    public void cleanup() throws Exception {
        Await.result(httpService.shutdown(), timeout);
        actorRuntime.actorSystem().terminate();
        Await.result(actorRuntime.actorSystem().whenTerminated(), timeout);
        Await.result(serverWiring.actorRuntime().classicSystem().terminate(), timeout);
        Await.result(locationWiring.actorRuntime().shutdown(), timeout);
    }
}
