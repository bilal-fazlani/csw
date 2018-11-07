package csw.testkit;

//#junit-testkit
import com.typesafe.config.ConfigFactory;
import csw.testkit.javadsl.FrameworkTestKitJunitResource;
import csw.testkit.javadsl.JCSWService;
import org.junit.ClassRule;
import org.junit.Test;
import org.scalatest.junit.JUnitSuite;

import java.util.Arrays;

public class JUnitIntegrationExampleTest extends JUnitSuite {

    @ClassRule
    public static final FrameworkTestKitJunitResource testKit =
            new FrameworkTestKitJunitResource(Arrays.asList(JCSWService.AlarmStore, JCSWService.EventStore));

    @Test
    public void testSpwaningComponentInStandaloneMode() {
        testKit.spawnStandalone(ConfigFactory.load("SampleHcdStandalone.conf"));

         // ... assertions etc.
    }
}
//#junit-testkit