/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.config.api

import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.scaladsl.{FileIO, Keep, Source, StreamConverters}
import org.apache.pekko.util.ByteString
import com.typesafe.config.{Config, ConfigFactory}
import csw.config.api.commons.Constants

import scala.concurrent.Future
import scala.jdk.FutureConverters.*

/**
 * This class represents the contents of the files being managed.
 * It wraps an Pekko streams of ByteString.
 *
 * @param source an pekko source that materializes to stream of bytes
 * @param length the length representing number of bytes
 */
class ConfigData private (val source: Source[ByteString, Any], val length: Long) {

  /**
   * Returns a future string by reading the source
   *
   * @param system an pekko system required to start the stream of file data that will form a string out of bytes
   * @return a future that completes with string representation of file data
   */
  def toStringF(implicit system: ActorSystem[?]): Future[String] =
    source.runFold("")((str, bs) => str + bs.utf8String)

  /**
   * Returns a future of Config object if the data is in valid parseable HOCON format. Else, throws ConfigException.
   *
   * @param system an pekko system required to start the stream of file data that will form a string out of bytes
   *            and parse it to `Config` model
   * @return a future that completes with `Config` model representing the file data
   */
  def toConfigObject(implicit system: ActorSystem[?]): Future[Config] = {
    import system.executionContext
    toStringF.map { s => ConfigFactory.parseString(s) }
  }

  /**
   * Java API that returns a future string by reading the source.
   *
   * @param system required to start the stream of file data that will form a string out of bytes
   * @return a CompletableFuture that completes with string representation of file data
   */
  private[config] def toJStringF(implicit system: ActorSystem[?]): CompletableFuture[String] =
    toStringF.asJava.toCompletableFuture

  /**
   * Returns a future of Config object if the data is in valid parseable HOCON format. Else, throws ConfigException.
   *
   * @param system an pekko system required to start the stream of file data that will form a string out of bytes
   *               and parse it to `Config` model
   * @return a CompletableFuture that completes with `Config` model representing the file data
   */
  private[config] def toJConfigObject(implicit system: ActorSystem[?]): CompletableFuture[Config] =
    toConfigObject.asJava.toCompletableFuture

  /**
   * Writes config data to a provided file path and returns future file
   *
   * @param path the path to which the file data from config service is dumped on local machine
   * @param system an pekko system required to start the stream of file data and dump it onto the provided `path`
   * @return a future of path that represents the file path on local machine
   */
  def toPath(path: Path)(implicit system: ActorSystem[?]): Future[Path] = {
    import system.executionContext
    source
      .toMat(FileIO.toPath(path))(Keep.right)
      .mapMaterializedValue { resultF => resultF.map(_ => path) }
      .run()
  }

  /**
   * Returns an inputStream which emits the bytes read from source of file data
   *
   * @param system an pekko system required to start the stream of file data and convert it to InputStream
   * @return an inputStream which emits the bytes read from source of file data
   */
  private[config] def toInputStream(implicit system: ActorSystem[?]): InputStream =
    source.runWith(StreamConverters.asInputStream())
}

/**
 * Provides various alternatives for constructing the data to be stored in the config service.
 */
object ConfigData {

  /**
   * Create ConfigData from string
   *
   * @param str the string which needs to be converted to ConfigData
   * @return the ConfigData created from the given string
   */
  def fromString(str: String): ConfigData = ConfigData.fromBytes(str.getBytes())

  /**
   * Create ConfigData from file path
   *
   * @param path the path which needs to be converted to ConfigData
   * @return the ConfigData created from the given path
   */
  def fromPath(path: Path): ConfigData = ConfigData.from(FileIO.fromPath(path), path.toFile.length())

  /**
   * Create ConfigData from byte array
   *
   * @param bytes an array of bytes that is converted to ConfigData
   * @return the ConfigData created from the given array of bytes
   */
  def fromBytes(bytes: Array[Byte]): ConfigData = ConfigData.from(Source.single(ByteString(bytes)), bytes.length)

  /**
   * An internally used method to create that take an pekko source and length of data and creates a ConfigData instance
   * out of it
   *
   * @param dataBytes an pekko source that materializes to stream of bytes
   * @param length the length representing number of bytes
   * @return the ConfigData instance created out of provided dataBytes and length
   */
  private[config] def from(dataBytes: Source[ByteString, Any], length: Long): ConfigData = {
    if (length == 0L) ConfigData.fromString(Constants.EmptySourceContent)
    else new ConfigData(dataBytes, length)
  }

}
