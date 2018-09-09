package com.emarsys.scheduler

object Algebra {
  sealed trait ScheduleOp[F[_], A]
  case class After[F[_]](d: FDur, F: F[Unit])            extends ScheduleOp[F, Unit]
  case class Repeat[F[_]](F: F[Unit], i: FDur)           extends ScheduleOp[F, Unit]
  case class RepeatA[F[_]](d: FDur, F: F[Unit], i: FDur) extends ScheduleOp[F, Unit]

  type Schedule[F[_], A] = cats.free.Free[ScheduleOp[F, ?], A]
}
