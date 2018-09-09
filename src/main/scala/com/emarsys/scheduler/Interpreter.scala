package com.emarsys.scheduler

import cats.~>
import cats.Monad
import cats.data.State
import cats.effect.Timer

object Interpreter {
  import Algebra._
  import cats.syntax.all._

  type ScheduleState[F[_], A] = State[List[F[Unit]], A]

  def interpret[F[_]: Monad](implicit timer: Timer[F]) = {
    def after(d: FDur, F: F[Unit]) = timer.sleep(d) *> F
    def repeat(F: F[Unit], i: FDur): F[Unit] =
      F *> timer.sleep(i) >> repeat(F, i)

    new (ScheduleOp[F, ?] ~> ScheduleState[F, ?]) {
      def apply[A](s: ScheduleOp[F, A]) = s match {
        case After(d, F)          => State.modify(fs => after(d, F) :: fs)
        case Repeat(F, i)         => State.modify(fs => repeat(F, i) :: fs)
        case RepeatAfter(d, F, i) => State.modify(fs => after(d, repeat(F, i)) :: fs)
      }
    }
  }
}
