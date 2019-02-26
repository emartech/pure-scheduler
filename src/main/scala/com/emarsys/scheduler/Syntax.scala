package com.emarsys.scheduler

import cats.{Functor, Monad}
import cats.effect.Timer
import Schedule.Decision

import scala.concurrent.duration.FiniteDuration

trait Syntax {
  implicit def toScheduleOps[F[_], A](fa: F[A])                          = new ScheduleOps(fa)
  implicit def toCombinators[F[_], S, A, B](s: Schedule.Aux[F, S, A, B]) = new ScheduleCombinators(s)
}

final class ScheduleOps[F[_], A](fa: F[A]) {
  def runOn[S, B](schedule: Schedule.Aux[F, S, A, B])(implicit M: Monad[F], T: Timer[F]) =
    Schedule.run(fa, schedule)
}

final class ScheduleCombinators[F[_], S, A, B](schedule: Schedule.Aux[F, S, A, B]) {
  def after(delay: FiniteDuration)(implicit F: Functor[F])             = Schedule.after(schedule, delay)
  def delay(interval: FiniteDuration)(implicit F: Functor[F])          = Schedule.delay(schedule, interval)
  def reconsider(f: Decision[S, B] => Boolean)(implicit F: Functor[F]) = Schedule.reconsider(schedule, f)
}
