/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.event.client.perf.reporter

import java.io.{File, OutputStream}
import java.nio.file.{Files, StandardOpenOption}

import org.apache.pekko.actor.typed.ActorSystem
import com.typesafe.config.Config

/**
 * Simple to file logger for benchmark results. Will log relevant settings first to make sure
 * results can be understood later.
 */
trait BenchmarkFileReporter {
  val fos: OutputStream
  def testName: String
  def reportResults(result: String): Unit
  def close(): Unit
}

object BenchmarkFileReporter {
  val targetDirectory: File = {
    val target = new File("csw-event-client/target/benchmark-results")
    target.mkdirs()
    target
  }
  val dir = "csw-event-client/target/benchmark-results"

  def apply(test: String, system: ActorSystem[_], logSettings: Boolean = true): BenchmarkFileReporter =
    new BenchmarkFileReporter {
      override val testName: String = test

      val testResultFile: File = {
        val fileName = s"$dir/$testName-results.txt"
        val file     = new File(fileName)
        Files.deleteIfExists(file.toPath)
        file.getParentFile.mkdirs()
        file.createNewFile()
        file
      }
      val config: Config = system.settings.config

      override val fos: OutputStream = Files.newOutputStream(testResultFile.toPath, StandardOpenOption.APPEND)

      val settingsToReport =
        Seq(
          "csw.event.client.perf.totalMessagesFactor",
          "pekko.remote.default-remote-dispatcher.throughput",
          "pekko.remote.default-remote-dispatcher.fork-join-executor.parallelism-factor",
          "pekko.remote.default-remote-dispatcher.fork-join-executor.parallelism-min",
          "pekko.remote.default-remote-dispatcher.fork-join-executor.parallelism-max"
        )
      if (logSettings) settingsToReport.foreach(reportSetting)

      def reportResults(result: String): Unit = synchronized {
        println(result)
        fos.write(result.getBytes("utf8"))
        fos.write('\n')
        fos.flush()
      }

      def reportSetting(name: String): Unit = {
        val value = if (config.hasPath(name)) config.getString(name) else "[unset]"
        reportResults(s"$name: $value")
      }

      def close(): Unit = fos.close()
    }
}
