package csw.services.alarm.client.internal

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.lettuce.core.pubsub.api.reactive.{PatternMessage, RedisPubSubReactiveCommands}
import reactor.core.publisher.FluxSink.OverflowStrategy

import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.{ExecutionContext, Future}

class RedisReactiveScalaApi[K, V](redisReactiveCommands: RedisPubSubReactiveCommands[K, V])(implicit ec: ExecutionContext) {
  def psubscribe(keys: List[K]): Future[Unit]   = redisReactiveCommands.psubscribe(keys: _*).toFuture.toScala.map(_ ⇒ ())
  def punsubscribe(keys: List[K]): Future[Unit] = redisReactiveCommands.punsubscribe(keys: _*).toFuture.toScala.map(_ ⇒ ())
  def quit: Future[String]                      = redisReactiveCommands.quit().toFuture.toScala
  def observePatterns(overflowStrategy: OverflowStrategy): Source[PatternMessage[K, V], NotUsed] =
    Source.fromPublisher(redisReactiveCommands.observePatterns(overflowStrategy))
}
