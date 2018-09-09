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
        case After(d, fa)      => State.modify(fs => after(d, fa) :: fs)
        case Repeat(fa, i)     => State.modify(fs => repeat(fa, i) :: fs)
        case RepeatA(d, fa, i) => State.modify(fs => after(d, repeat(fa, i)) :: fs)
      }
    }
  }
}
