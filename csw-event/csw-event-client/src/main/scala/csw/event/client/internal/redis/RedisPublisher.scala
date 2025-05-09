/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.event.client.internal.redis

import org.apache.pekko.Done
import org.apache.pekko.actor.Cancellable
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import csw.event.api.exceptions.PublishFailure
import csw.event.api.scaladsl.EventPublisher
import csw.event.client.internal.commons.EventPublisherUtil
import csw.params.events.Event
import csw.time.core.models.TMTTime
import io.lettuce.core.{RedisClient, RedisURI}
import romaine.RomaineFactory
import romaine.async.RedisAsyncApi

import cps.compat.FutureAsync.*
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

/**
 * An implementation of [[csw.event.api.scaladsl.EventPublisher]] API which uses Redis as the provider for publishing
 * and subscribing events.
 *
 * @param redisURI    future containing connection details for the Redis/Sentinel connections.
 * @param redisClient redis client available from lettuce
 * @param actorSystem provides Materializer, executionContext, etc
 */
private[event] class RedisPublisher(redisURI: Future[RedisURI], redisClient: RedisClient)(implicit
    actorSystem: ActorSystem[?]
) extends EventPublisher {

  import actorSystem.executionContext

  // inorder to preserve the order of publishing events, the parallelism level is maintained to 1
  private val parallelism                         = 1
  private val defaultInitialDelay: FiniteDuration = 0.millis
  private val eventPublisherUtil                  = new EventPublisherUtil()
  private val romaineFactory                      = new RomaineFactory(redisClient)
  import EventRomaineCodecs._

  private val asyncApi: RedisAsyncApi[String, Event] = romaineFactory.redisAsyncApi(redisURI)

  private val streamTermination: Future[Done] = eventPublisherUtil.streamTermination(publishInternal)

  // This blocks main thread and publish dummy initialization event.
  // We have observed higher latencies for few initial events with [[EventPublisher.publish(event: Event)]] API when used for periodic publish.
  // This will make sure single initialize event is published and publisher is completely initialized/warmed up before handing over [[EventPublisher]] handle to user.
  publishInitializationEvent()

  override def publish(event: Event): Future[Done] = eventPublisherUtil.publish(event, streamTermination.isCompleted)

  private def publishInternal(event: Event): Future[Done] =
    async {
      await(asyncApi.publish(event.eventKey.key, event))
      set(event, asyncApi) // set will run independent of publish
      Done
    } recover { case NonFatal(ex) =>
      val failure = PublishFailure(event, ex)
      eventPublisherUtil.logError(failure)
      throw failure
    }

  override def publish[Mat](source: Source[Event, Mat]): Mat =
    eventPublisherUtil.publishFromSource(source, parallelism, publishInternal, None)

  override def publish[Mat](source: Source[Event, Mat], onError: PublishFailure => Unit): Mat =
    eventPublisherUtil.publishFromSource(source, parallelism, publishInternal, Some(onError))

  override def publish(eventGenerator: => Option[Event], every: FiniteDuration): Cancellable =
    publish(eventPublisherUtil.eventSource(Future.successful(eventGenerator), parallelism, defaultInitialDelay, every))

  override def publish(eventGenerator: => Option[Event], startTime: TMTTime, every: FiniteDuration): Cancellable =
    publish(eventPublisherUtil.eventSource(Future.successful(eventGenerator), parallelism, startTime.durationFromNow, every))

  override def publish(eventGenerator: => Option[Event], every: FiniteDuration, onError: PublishFailure => Unit): Cancellable =
    publish(eventPublisherUtil.eventSource(Future.successful(eventGenerator), parallelism, defaultInitialDelay, every), onError)

  override def publish(
      eventGenerator: => Option[Event],
      startTime: TMTTime,
      every: FiniteDuration,
      onError: PublishFailure => Unit
  ): Cancellable =
    publish(
      eventPublisherUtil.eventSource(Future.successful(eventGenerator), parallelism, startTime.durationFromNow, every),
      onError
    )

  override def publishAsync(eventGenerator: => Future[Option[Event]], every: FiniteDuration): Cancellable =
    publish(eventPublisherUtil.eventSource(eventGenerator, parallelism, defaultInitialDelay, every))

  override def publishAsync(eventGenerator: => Future[Option[Event]], startTime: TMTTime, every: FiniteDuration): Cancellable =
    publish(eventPublisherUtil.eventSource(eventGenerator, parallelism, startTime.durationFromNow, every))

  override def publishAsync(
      eventGenerator: => Future[Option[Event]],
      every: FiniteDuration,
      onError: PublishFailure => Unit
  ): Cancellable =
    publish(eventPublisherUtil.eventSource(eventGenerator, parallelism, defaultInitialDelay, every), onError)

  override def publishAsync(
      eventGenerator: => Future[Option[Event]],
      startTime: TMTTime,
      every: FiniteDuration,
      onError: PublishFailure => Unit
  ): Cancellable =
    publish(eventPublisherUtil.eventSource(eventGenerator, parallelism, startTime.durationFromNow, every), onError)

  override def shutdown(): Future[Done] = {
    eventPublisherUtil.shutdown()
    asyncApi.quit().map(_ => Done)
  }

  private def set(event: Event, commands: RedisAsyncApi[String, Event]): Future[Done] =
    commands.set(event.eventKey.key, event).recover { case NonFatal(_) => Done }

  private def publishInitializationEvent() = Await.result(publish(InitializationEvent.value), 30.seconds)

}
