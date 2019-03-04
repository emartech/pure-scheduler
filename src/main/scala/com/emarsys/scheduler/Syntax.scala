package com.emarsys.scheduler

import cats.{Functor, Monad}
import cats.effect.Timer
import Schedule.Decision

import scala.concurrent.duration.FiniteDuration

trait Syntax {
  implicit def toScheduleOps[F[+ _]: Monad: Timer, A](fa: F[A])           = new ScheduleOps(fa)
  implicit def toCombinators[F[+ _]: Functor, A, B](s: Schedule[F, A, B]) = new ScheduleCombinators(s)
}

final class ScheduleOps[F[+ _]: Monad: Timer, A](fa: F[A]) {
  def runOn[B](schedule: Schedule[F, A, B]) = Schedule.run(fa, schedule)
}

final class ScheduleCombinators[F[+ _]: Functor, A, B](schedule: Schedule[F, A, B]) {
  def after(delay: FiniteDuration)                          = Schedule.after(schedule, delay)
  def delay(interval: FiniteDuration)                       = Schedule.delay(schedule, interval)
  def reconsider(f: Decision[schedule.State, B] => Boolean) = Schedule.reconsider(schedule)(f)
}
