/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.benchmark.command

import java.util.concurrent.TimeUnit

import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.util
import com.typesafe.config.ConfigFactory
import csw.benchmark.command.BenchmarkHelpers.spawnStandaloneComponent
import csw.command.api.scaladsl.CommandService
import csw.location.server.internal.ServerWiring
import csw.params.commands
import csw.params.commands.CommandName
import csw.params.commands.CommandResponse.SubmitResponse
import csw.prefix.models.Prefix
import org.openjdk.jmh.annotations.*

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

// ============================== HOW TO RUN THIS TEST: ====================================
//
// single thread:
// sbt csw-benchmark/jmh:run -f 1 -wi 10 -i 20 .*CommandServiceBenchmark.*
//
// multiple threads (for example, 4 threads):
// sbt csw-benchmark/jmh:run -f 1 -wi 10 -i 20 -t 4 -si true .*CommandServiceBenchmark.*
//

// DEOPSCSW-231 :Measure Performance of Command Service
@State(Scope.Benchmark)
@Fork(1)
@Threads(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS, batchSize = 1)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS, batchSize = 1)
class CommandServiceBenchmark {

  implicit var timeout: util.Timeout    = scala.compiletime.uninitialized
  implicit var scheduler: Scheduler     = scala.compiletime.uninitialized
  var setupCommand: commands.Setup      = scala.compiletime.uninitialized
  var componentRef: CommandService      = scala.compiletime.uninitialized
  private var adminWiring: ServerWiring = scala.compiletime.uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    adminWiring = ServerWiring.make(Some(3553), enableAuth = false)
    Await.result(adminWiring.locationHttpService.start(), 5.seconds)
    componentRef = spawnStandaloneComponent(adminWiring.actorSystem, ConfigFactory.load("standalone.conf"))
    setupCommand = commands.Setup(Prefix("wfos.blue.filter"), CommandName("jmh"), None)
    timeout = util.Timeout(5.seconds)
    scheduler = adminWiring.actorSystem.scheduler
  }

  @TearDown(Level.Trial)
  def teardown(): Unit = {
    Await.result(adminWiring.actorRuntime.shutdown(), 5.seconds)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def commandThroughput(): SubmitResponse = {
    Await.result(componentRef.submitAndWait(setupCommand), 5.seconds)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def commandLatency(): SubmitResponse = {
    Await.result(componentRef.submitAndWait(setupCommand), 5.seconds)
  }
}
