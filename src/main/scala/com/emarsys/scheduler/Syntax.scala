package com.emarsys.scheduler

import cats.{Apply, Monad}
import cats.effect.Timer
import Schedule.Decision

import scala.concurrent.duration.FiniteDuration

trait Syntax {
  implicit def toScheduleOps[F[+ _]: Monad: Timer, A](fa: F[A])         = new ScheduleOps(fa)
  implicit def toCombinators[F[+ _]: Apply, A, B](s: Schedule[F, A, B]) = new ScheduleCombinators(s)
}

final class ScheduleOps[F[+ _]: Monad: Timer, A](fa: F[A]) {
  def runOn[B](schedule: Schedule[F, A, B]) = Schedule.run(fa, schedule)
}

final class ScheduleCombinators[F[+ _]: Apply, A, B](schedule: Schedule[F, A, B]) {
  import cats.syntax.functor._

  def after(delay: FiniteDuration)                          = Schedule.after(schedule, delay)
  def delay(interval: FiniteDuration)                       = Schedule.delay(schedule, interval)
  def reconsider(f: Decision[schedule.State, B] => Boolean) = Schedule.reconsider(schedule)(f)
  def &&[C](that: Schedule[F, A, C])                        = Schedule.combine(schedule, that)(_ && _)(_ max _)
  def ||[C](that: Schedule[F, A, C])                        = Schedule.combine(schedule, that)(_ || _)(_ min _)
  def <*[C](that: Schedule[F, A, C])                        = &&(that) map (_._1)
  def *>[C](that: Schedule[F, A, C])                        = &&(that) map (_._2)
}
