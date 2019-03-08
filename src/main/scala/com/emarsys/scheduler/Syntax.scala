package com.emarsys.scheduler

import cats.Monad
import cats.effect.Timer
import Schedule.Decision

import scala.concurrent.duration.FiniteDuration

trait Syntax {
  implicit def toScheduleOps[F[+ _]: Monad: Timer, A](fa: F[A])         = new ScheduleOps(fa)
  implicit def toCombinators[F[+ _]: Monad, A, B](s: Schedule[F, A, B]) = new ScheduleCombinators(s)
}

final class ScheduleOps[F[+ _]: Monad: Timer, A](fa: F[A]) {
  def runOn[B](schedule: Schedule[F, A, B]) = Schedule.run(fa, schedule)
}

final class ScheduleCombinators[F[+ _]: Monad, A, B](schedule: Schedule[F, A, B]) {
  import cats.syntax.functor._

  def after(delay: FiniteDuration)                          = Schedule.after(schedule, delay)
  def space(interval: FiniteDuration)                       = Schedule.space(schedule, interval)
  def reconsider(f: Decision[schedule.State, B] => Boolean) = Schedule.reconsider(schedule)(f)
  def &&[A1 <: A, C](that: Schedule[F, A1, C])              = Schedule.combine(schedule, that)(_ && _)(_ max _)
  def ||[A1 <: A, C](that: Schedule[F, A1, C])              = Schedule.combine(schedule, that)(_ || _)(_ min _)
  def <*[A1 <: A, C](that: Schedule[F, A1, C])              = &&(that) map (_._1)
  def *>[A1 <: A, C](that: Schedule[F, A1, C])              = &&(that) map (_._2)
  def andAfterThat(second: Schedule[F, A, B])               = Schedule.chain(schedule, second)
  def >>>[C](that: Schedule[F, B, C])                       = Schedule.compose(schedule, that)
  def <<<[A0](that: Schedule[F, A0, A])                     = Schedule.compose(that, schedule)
  def fold[Z](z: Z)(c: (Z, B) => Z)                         = Schedule.fold(schedule)(z)(c)
  def collect                                               = fold[List[B]](Nil)((bs, b) => b :: bs) map (_.reverse)
}
