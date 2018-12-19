package csw.time.client.internal

import java.time.Duration
import java.util.concurrent.TimeUnit.NANOSECONDS

import akka.actor.{ActorRef, ActorSystem, Scheduler}
import csw.time.api.models.Cancellable
import csw.time.api.{TAITime, TMTTime, TimeServiceScheduler, UTCTime}
import csw.time.client.internal.extensions.RichCancellableExt.RichCancellable

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class TimeServiceSchedulerImpl(implicit actorSystem: ActorSystem) extends TimeServiceScheduler {
  import actorSystem.dispatcher

  private val scheduler: Scheduler = actorSystem.scheduler

  // ========== scheduleOnce ==========
  override def scheduleOnce(startTime: TMTTime)(f: ⇒ Unit): Cancellable =
    scheduler.scheduleOnce(delayFrom(startTime))(f).toTsCancellable

  override def scheduleOnce(startTime: TMTTime, runnable: Runnable): Cancellable =
    scheduler.scheduleOnce(delayFrom(startTime), runnable).toTsCancellable

  override def scheduleOnce(startTime: TMTTime, receiver: ActorRef, message: Any): Cancellable =
    scheduler.scheduleOnce(delayFrom(startTime), receiver, message).toTsCancellable

  // ========== schedulePeriodically ==========
  override def schedulePeriodically(interval: Duration, runnable: Runnable): Cancellable =
    scheduler.schedule(0.millis, FiniteDuration(interval.toNanos, NANOSECONDS), runnable).toTsCancellable

  override def schedulePeriodically(interval: Duration)(f: ⇒ Unit): Cancellable =
    scheduler.schedule(0.millis, FiniteDuration(interval.toNanos, NANOSECONDS))(f).toTsCancellable

  override def schedulePeriodically(interval: Duration, receiver: ActorRef, message: Any): Cancellable =
    scheduler.schedule(0.millis, FiniteDuration(interval.toNanos, NANOSECONDS), receiver, message).toTsCancellable

  // ========== schedulePeriodically with start time ==========
  override def schedulePeriodically(startTime: TMTTime, interval: Duration, runnable: Runnable): Cancellable =
    scheduler.schedule(delayFrom(startTime), FiniteDuration(interval.toNanos, NANOSECONDS), runnable).toTsCancellable

  override def schedulePeriodically(startTime: TMTTime, interval: Duration)(f: ⇒ Unit): Cancellable =
    scheduler.schedule(delayFrom(startTime), FiniteDuration(interval.toNanos, NANOSECONDS))(f).toTsCancellable

  override def schedulePeriodically(startTime: TMTTime, interval: Duration, receiver: ActorRef, message: Any): Cancellable =
    scheduler.schedule(delayFrom(startTime), FiniteDuration(interval.toNanos, NANOSECONDS), receiver, message).toTsCancellable

  private def delayFrom(time: TMTTime): FiniteDuration = {
    val now      = instantFor(time)
    val duration = Duration.between(now, time.value)
    FiniteDuration(duration.toNanos, NANOSECONDS)
  }

  private def instantFor(time: TMTTime) = time match {
    case _: UTCTime ⇒ UTCTime.now().value
    case _: TAITime ⇒ TAITime.now().value
  }
}