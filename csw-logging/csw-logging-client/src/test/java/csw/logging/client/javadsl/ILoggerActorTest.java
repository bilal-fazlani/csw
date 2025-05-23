/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.logging.client.javadsl;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.SpawnProtocol;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import csw.logging.client.appenders.LogAppenderBuilder;
import csw.logging.client.commons.PekkoTypedExtension;
import csw.logging.client.commons.LoggingKeys$;
import csw.logging.client.components.trombone.JTromboneHCDSupervisorActor;
import csw.logging.client.internal.LoggingSystem;
import csw.logging.client.utils.JLogUtil;
import csw.logging.client.utils.TestAppender;
import csw.logging.models.Level;
import csw.logging.models.Level$;
import csw.prefix.models.Prefix;
import org.junit.*;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static csw.logging.client.utils.Eventually.eventually;

// CSW-78: PrefixRedesign for logging
// CSW-86: Subsystem should be case-insensitive
// DEOPSCSW-316: Improve Logger accessibility for component developers
public class ILoggerActorTest {
    protected static final ActorSystem<SpawnProtocol.Command> actorSystem = ActorSystem.create(SpawnProtocol.create(), "base-system");
    protected static LoggingSystem loggingSystem;

    protected static final List<JsonObject> logBuffer = new ArrayList<>();

    protected static JsonObject parse(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, JsonElement.class).getAsJsonObject();
    }

    protected static final TestAppender testAppender = new TestAppender(x -> {
        logBuffer.add(parse(x.toString()));
        return null;
    });
    private static final List<LogAppenderBuilder> appenderBuilders = List.of(testAppender);

    @BeforeClass
    public static void setup() {
        loggingSystem = JLoggingSystemFactory.start("Logger-Test", "SNAPSHOT-1.0", "localhost", actorSystem, appenderBuilders);
    }

    @After
    public void afterEach() {
        logBuffer.clear();
    }

    @AfterClass
    public static void teardown() throws Exception {
        loggingSystem.javaStop().get();
        actorSystem.terminate();
        Await.result(actorSystem.whenTerminated(), Duration.create(10, TimeUnit.SECONDS));
    }

    @Test
    public void testDefaultLogConfigurationForActor_DEOPSCSW_316() {

        PekkoTypedExtension.UserActorFactory userActorFactory = PekkoTypedExtension.UserActorFactory(actorSystem);

        ActorRef<String> tromboneActor = userActorFactory.<String>spawn(JTromboneHCDSupervisorActor.behavior(new JLoggerFactory(Prefix.apply("csw.jTromboneHcdActor"))), "csw.JTromboneActor", org.apache.pekko.actor.typed.Props.empty());

        String actorPath = tromboneActor.path().toString();
        String className = JTromboneHCDSupervisorActor.class.getName();

        JLogUtil.sendLogMsgToActorInBulk(tromboneActor);

        eventually(java.time.Duration.ofSeconds(10), () -> Assert.assertEquals(4, logBuffer.size()));

        logBuffer.forEach(log -> {
            Assert.assertEquals("jTromboneHcdActor", log.get(LoggingKeys$.MODULE$.COMPONENT_NAME()).getAsString());
            Assert.assertEquals("CSW", log.get(LoggingKeys$.MODULE$.SUBSYSTEM()).getAsString());
            Assert.assertEquals("CSW.jTromboneHcdActor", log.get(LoggingKeys$.MODULE$.PREFIX()).getAsString());
            Assert.assertEquals(actorPath, log.get(LoggingKeys$.MODULE$.ACTOR()).getAsString());

            Assert.assertTrue(log.has(LoggingKeys$.MODULE$.SEVERITY()));
            String severity = log.get(LoggingKeys$.MODULE$.SEVERITY()).getAsString().toLowerCase();

            Assert.assertEquals(severity, log.get(LoggingKeys$.MODULE$.MESSAGE()).getAsString());
            Assert.assertEquals(className, log.get(LoggingKeys$.MODULE$.CLASS()).getAsString());

            Level currentLogLevel = Level$.MODULE$.apply(severity);
            Assert.assertTrue(currentLogLevel.$greater$eq(Level.INFO$.MODULE$));
        });
    }

}
