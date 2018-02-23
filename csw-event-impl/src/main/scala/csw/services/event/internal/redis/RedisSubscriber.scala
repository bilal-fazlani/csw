package csw.services.event.internal.redis

import akka.Done
import akka.stream.scaladsl.{Keep, Source}
import akka.stream.{KillSwitches, Materializer}
import csw.messages.ccs.events.{Event, EventKey}
import csw.services.event.scaladsl.{EventSubscriber, EventSubscription}
import io.lettuce.core.{RedisClient, RedisURI}
import reactor.core.publisher.FluxSink.OverflowStrategy

import scala.async.Async._
import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.{ExecutionContext, Future}

class RedisSubscriber(redisClient: RedisClient, redisURI: RedisURI)(implicit ec: ExecutionContext,
                                                                    protected val mat: Materializer)
    extends EventSubscriber { outer =>

  override def subscribe(eventKeys: Set[EventKey]): Source[Event, EventSubscription] = {
    val connectionF = redisClient.connectPubSubAsync(EventServiceCodec, redisURI).toScala

    val sourceF = async {
      val commands = await(connectionF).reactive()
      commands.subscribe(eventKeys.toSeq: _*).subscribe()
      Source.fromPublisher(commands.observeChannels(OverflowStrategy.LATEST)).map(_.getMessage)
    }

    Source
      .fromFutureSource(sourceF)
      .viaMat(KillSwitches.single)(Keep.right)
      .watchTermination()(Keep.both)
      .mapMaterializedValue {
        case (killSwitch, doneF) =>
          new EventSubscription {
            override def unsubscribe(): Future[Done] = async {
              val commands = await(connectionF).reactive()
              await(commands.unsubscribe(eventKeys.toSeq: _*).toFuture.toScala)
              killSwitch.shutdown()
              await(doneF)
            }
          }
      }
  }
}
