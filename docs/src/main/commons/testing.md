# Testing

## Dependencies

To use the CSW Testkit, you must add the following dependency in your project:

sbt
:   @@@vars
    ```scala
    libraryDependencies += "com.github.tmtsoftware.csw" %% "csw-testkit" % "$version$"
    ```
    @@@

## Introduction

CSW comes with a dedicated `csw-testkit` module for supporting tests. This module includes following multiple individual testkits:

- `LocationTestKit` : starts and stops the Location Server
- `ConfigTestKit` : starts and stops the Config Server
- `EventTestKit` : starts and stops the Event Service (Note : This uses `embedded-redis` to start redis sentinel and master)
- `AlarmTestKit` : starts and stops the Alarm Service (Note : This uses `embedded-redis` to start redis sentinel and master)
- `DatabaseTestKit` : starts and stops the Database Service (Note : This uses `embedded postgres`)
- `FrameworkTestKit` : in most of the cases, you will end up using this testkit. `FrameworkTestKit` is created by composing all the above mentioned testkits. Hence, it supports starting and stopping all provided CSW services.

@@@ note

All the testkits require the Location Server to be up and running. Hence, the first thing all testkits do is to start a Location Server.
You do not need to start it explicitly.

@@@

## TestKits

When you really want granular level access to testkits, then only you would want to use `LocationTestKit`|`ConfigTestKit`|`EventTestKit`|`AlarmTestKit`|`DatabaseTestKit`|`FrameworkTestKit` directly.
You can create instance of `FrameworkTestKit` as shown below:

Scala
:   @@snip [TestKitsExample.scala](../../../../examples/src/test/scala/example/teskit/TestKitsExample.scala) { #framework-testkit }

Java
:   @@snip [TestKitsExample.scala](../../../../examples/src/test/java/example/testkit/JTestKitsExample.java) { #framework-testkit }

@@@ note

Similarly, you can use other testkits. Please refer to API docs for more details.

@@@

### Enabling Logging

You can enable logging service using `LoggingSystemFactory.forTestingOnly()` factory.
This is useful for debugging purpose. It logs messages to console.

```scala
import csw.logging.client.scaladsl.LoggingSystemFactory

// make sure you have implicit actor system in scope or provide it explicitly
LoggingSystemFactory.forTestingOnly()
```

### Spawning components

`FrameworkTestKit` provides an easy way to spawn components in `Container` or `Standalone` mode.
Use the `spawnContainer` method provided by `FrameworkTestKit` to start components in container mode and`spawnStandalone` method to start a component in standalone mode.

The example below shows how to spawn container or component in standalone mode using the Framework testkit.

Scala
:   @@snip [TestKitsExample.scala](../../../../examples/src/test/scala/example/teskit/TestKitsExample.scala) { #spawn-using-testkit }

Java
:   @@snip [JTestKitsExample.scala](../../../../examples/src/test/java/example/testkit/JTestKitsExample.java) { #spawn-using-testkit }

Other ways to spawn Assembly and HCD in the standalone mode using Framework testkit.

## Spawning a HCD

Scala
:   @@snip [TestKitsExample.scala](../../../../examples/src/test/scala/example/teskit/TestKitsExample.scala) { #spawn-hcd }

## Spawning an Assembly

Scala
:   @@snip [TestKitsExample.scala](../../../../examples/src/test/scala/example/teskit/TestKitsExample.scala) { #spawn-assembly }

Full source at GitHub

- [Scala]($github.base_url$/examples/src/test/scala/example/teskit/TestKitsExample.scala)
- [Java]($github.base_url$/examples/src/test/java/example/testkit/JTestKitsExample.java)

## Test Framework Integration

### ScalaTest

If you are using ScalaTest, then you can extend `csw.testkit.scaladsl.ScalaTestFrameworkTestKit` to have the Framework testkit automatically start the provided services before running tests and shut them down when the tests are complete.
This is done in `beforeAll` and `afterAll` from the `BeforeAndAfterAll` trait. If you override that method you should call `super.beforeAll` to start services and `super.afterAll` to shutdown the test kit.

### JUnit

If you are using JUnit then you can use `csw.testkit.javadsl.FrameworkTestKitJunitResource` to have the framework test kit automatically start the provided services before running tests and shut them down when the tests are complete.

### Supported CSW Services by FrameworkTestKit

`ScalaTestFrameworkTestKit` and `FrameworkTestKitJunitResource` both support starting one or more of the following services.

| Scala                         | Java                          |
| -------------------------     | --------------------------    |
| CSWService.LocationServer     | JCSWService.LocationServer    |
| CSWService.ConfigServer       | JCSWService.ConfigServer      |
| CSWService.EventServer        | JCSWService.EventServer       |
| CSWService.AlarmServer        | JCSWService.AlarmServer       |
| CSWService.DatabaseServer     | JCSWService.DatabaseServer    |

The example below shows the usage of `ScalaTestFrameworkTestKit` and `FrameworkTestKitJunitResource` and how you can start the above mentioned services as per your need.

Scala
:   @@snip [ScalaTestExampleIntegration.scala](../../../../examples/src/test/scala/example/teskit/ScalaTestIntegrationExample.scala) { #scalatest-testkit }

Java
:   @@snip [JUnitIntegrationExample.scala](../../../../examples/src/test/java/example/testkit/JUnitIntegrationExample.java) { #junit-testkit }

@@@ note

You do not need to externally start any services like the Event Service, Config Service, Location Service etc. via `csw-services` application.
Testkits will start required services as a part of initialization. For the Event and Alarm service, it uses an instance of `embedded-redis`.

@@@

### Initializing alarms & getting severity of alarms

To use alarms in your tests, the list of available alarms must be defined prior to starting the Alarm Service.  This will
create alarms in the Alarm Store, and then the alarm severities can be set individually.  
See the @ref:[Alarm Service Documentation](../services/alarm.md) for more information on the
alarm configuration file.

The `AlarmTestKit` provides methods to initialize the alarm store, as well as get the current severity of
an alarm for testing purposes.

The example below shows the usage `initAlarms` & `getCurrentSeverity` methods using `AlarmTestKit`.

Scala
: @@snip [ScalaAlarmTestKitExample.scala](../../../../examples/src/test/scala/example/teskit/ScalaAlarmTestKitExample.scala) { #scalatest-alarm-testkit }

Java
:   @@snip [JUnitAlarmTestKitExample.scala](../../../../examples/src/test/java/example/testkit/JUnitAlarmTestKitExample.java) { #junit-alarm-testkit }

### Using dslContext provided by DatabaseTestKit

`DatabaseTestKit` starts embedded postgres with default database, user and password. You can override these defaults by adding custom configurations. For more details refer @ref:[Database Service Documentation](../services/database.md)

The `DatabaseTestKit` also provides methods to create database client and execute queries.

The example below shows the usage `dslContext` & `databaseServiceFactory` methods using `DatabaseTestKit`.

Scala
: @@snip [ScalaDatabaseTestKitExample.scala](../../../../examples/src/test/scala/example/teskit/ScalaDatabaseTestKitExample.scala) { #scalatest-database-testkit }

Java
:   @@snip [JUnitDatabaseTestKitExample.scala](../../../../examples/src/test/java/example/testkit/JUnitDatabaseTestKitExample.java) { #junit-database-testkit }

## Unit Tests

The goal of unit testing is to break your application into the smallest testable units and test them individually,
isolating a specific piece of functionality and ensuring it is working correctly.
It is always a good idea to write more unit test cases and relatively fewer component and integration tests.
If you want to get an idea of how many tests you should have in different types of testing phases (Unit/Component/Integration), refer to this [blog](https://martinfowler.com/articles/practical-test-pyramid.html)

Unit testing simple Scala/Java classes or objects is straightforward. You can mock external dependencies using Mockito. Refer to the @ref:[Mockito](#mockito) section for more details.

The following links provide guides for testing applications using different modules of Pekko:

- [Pekko Untyped Actors](https://doc.pekko.io/docs/pekko/current/testing.html)
- [Pekko Typed Actors](https://doc.pekko.io/docs/pekko/current/typed/testing.html)
- [Pekko Streams](https://doc.pekko.io/docs/pekko/current/stream/stream-testkit.html?language=scala)

## Multi-JVM Tests

Testing asynchronous distributed systems requires special tooling/framework support.
Sbt has a plugin called [sbt-multi-jvm](https://github.com/sbt/sbt-multi-jvm) which helps to test systems across multiple JVMs or machines.
This is especially useful for integration testing where multiple systems communicate with each other.

You can find more details on multi-JVM tests [here](https://doc.pekko.io/docs/pekko/current/multi-jvm-testing.html).

You can also refer to some examples in [CSW](https://github.com/tmtsoftware/csw) for writing your own multi-JVM tests. For example: [CommandServiceTest.scala]($github.base_url$/integration/src/multi-jvm/scala/csw/framework/command/CommandServiceTest.scala)  

In case you want to run your multi-JVM tests across machines, refer to this multi-node testing guide [here](https://doc.pekko.io/docs/pekko/current/multi-node-testing.html).

## Mockito

Mocks are used so that unit tests can be written independent of dependencies.  
[CSW](https://github.com/tmtsoftware/csw) uses [Mockito](https://site.mockito.org/) for writing unit tests.
ScalaTest comes with the [MockitoSugar](https://www.scalatest.org/plus/mockito) trait which provides some basic syntax sugar for Mockito.

For example: [ContainerBehaviorTest.scala]($github.base_url$/csw-framework/src/test/scala/csw/framework/internal/container/ContainerBehaviorTest.scala)
