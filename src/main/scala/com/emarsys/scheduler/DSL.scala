package com.emarsys.scheduler

import cats.Monad
import cats.Parallel
import cats.effect.Timer
import cats.free.Free.liftF

import scala.concurrent.duration._

trait DSL {
  import Algebra._
  import Interpreter._
  import cats.instances.list._
  import cats.syntax.parallel._

  def now[F[_]](F: F[Unit]): Schedule[F, Unit]                           = after(0.second, F)
  def after[F[_]](d: FDur, F: F[Unit]): Schedule[F, Unit]                = liftF(After(d, F))
  def repeat[F[_]](F: F[Unit], i: FDur): Schedule[F, Unit]               = liftF(Repeat(F, i))
  def repeatAfter[F[_]](d: FDur, F: F[Unit], i: FDur): Schedule[F, Unit] = liftF(RepeatA(d, F, i))

  def run[F[_]: Monad: Timer, G[_], A](schedule: Schedule[F, A])(implicit P: Parallel[F, G]): F[Unit] =
    schedule.foldMap(interpret).runS(Nil).value.parSequence_
}
