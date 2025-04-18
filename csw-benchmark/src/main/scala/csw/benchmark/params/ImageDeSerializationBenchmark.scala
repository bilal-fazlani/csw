/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.benchmark.params

import java.nio.file.{Files, Paths}
import java.util.concurrent.TimeUnit

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.serialization.{Serialization, SerializationExtension}
import csw.params.commands.{CommandName, Observe}
import csw.params.core.generics.KeyType.ByteArrayKey
import csw.params.core.generics.{Key, Parameter}
import csw.params.core.models.Units.pascal
import csw.params.core.models.{ArrayData, ObsId}
import csw.prefix.models.Prefix
import org.openjdk.jmh.annotations.*

import scala.concurrent.Await
import scala.concurrent.duration.DurationDouble

/**
 * Tests ImageDeSerializationBenchmark performance.
 */
// ============================== HOW TO RUN THIS TEST: ====================================
//
// single thread:
// sbt csw-benchmark/jmh:run -f 1 -wi 10 -i 20 .*ImageDeSerializationBenchmark.*
//
// multiple threads (for example, 4 threads):
// sbt csw-benchmark/jmh:run -f 1 -wi 10 -i 20 -t 4 -si true .*ImageDeSerializationBenchmark.*
//

// DEOPSCSW-187: Efficient serialization to/from binary
// DEOPSCSW-331: Complex payload - Include byte in paramset for Event and ObserveEvent
@State(Scope.Benchmark)
class ImageDeSerializationBenchmark {
  private final var system: ActorSystem[?]       = scala.compiletime.uninitialized
  private final var serialization: Serialization = scala.compiletime.uninitialized
  private final var prefixStr: String            = scala.compiletime.uninitialized
  private final var obsId: ObsId                 = scala.compiletime.uninitialized

  private final var img_32k_tuple: (Array[Byte], Observe)  = scala.compiletime.uninitialized
  private final var img_128k_tuple: (Array[Byte], Observe) = scala.compiletime.uninitialized
  private final var img_512k_tuple: (Array[Byte], Observe) = scala.compiletime.uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    system = ActorSystem(Behaviors.empty, "example")
    serialization = SerializationExtension(system)
    prefixStr = "wfos.prog.cloudcover"
    obsId = ObsId("2020A-001-123")

    img_32k_tuple = serializeImage("/images/32k_image.bin")
    img_128k_tuple = serializeImage("/images/128k_image.bin")
    img_512k_tuple = serializeImage("/images/512k_image.bin")
  }

  @TearDown(Level.Trial)
  def teardown(): Unit = {
    system.terminate()
    Await.result(system.whenTerminated, 5.seconds)
  }

  def serializeImage(file: String): (Array[Byte], Observe) = {
    val path                           = Paths.get(getClass.getResource(file).getPath)
    val binaryData                     = Files.readAllBytes(path)
    val imageKey: Key[ArrayData[Byte]] = ByteArrayKey.make("imageKey")

    val binaryImgData: ArrayData[Byte]    = ArrayData.fromArray(binaryData)
    val param: Parameter[ArrayData[Byte]] = imageKey -> binaryImgData `withUnits` pascal

    val observe           = Observe(Prefix("csw.originationPrefix"), CommandName(prefixStr), Some(obsId)).add(param)
    val observeSerializer = serialization.findSerializerFor(observe)

    (observeSerializer.toBinary(observe), observe)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def _032kImageDeSerializationBench(): Observe = {
    val observeSerializer = serialization.findSerializerFor(img_32k_tuple._2)
    observeSerializer.fromBinary(img_32k_tuple._1).asInstanceOf[Observe]
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def _128kImageDeSerializationBench(): Observe = {
    val observeSerializer = serialization.findSerializerFor(img_128k_tuple._2)
    observeSerializer.fromBinary(img_128k_tuple._1).asInstanceOf[Observe]
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def _512kImageDeSerializationBench(): Observe = {
    val observeSerializer = serialization.findSerializerFor(img_512k_tuple._2)
    observeSerializer.fromBinary(img_512k_tuple._1).asInstanceOf[Observe]
  }
}
