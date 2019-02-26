package com.emarsys.scheduler

import cats.{Applicative, Bifunctor, Eq, Functor, Monad}
import cats.arrow.Profunctor
import cats.effect.{Async, Timer}
import cats.syntax.all._

import scala.concurrent.duration._

trait Schedule[F[_], A, B] {
  type State
  val initial: F[Schedule.Init[State]]
  val update: (A, State) => F[Schedule.Decision[State, B]]
}

object Schedule extends Scheduler with ScheduleInstances with PredefinedSchedules with Combinators {
  type Aux[F[_], S, A, B] = Schedule[F, A, B] { type State = S }

  final case class Init[S](delay: FiniteDuration, state: S)
  final case class Decision[S, B](continue: Boolean, delay: FiniteDuration, state: S, result: B)

  def apply[F[_], S, A, B](
      initial0: F[Init[S]],
      update0: (A, S) => F[Decision[S, B]]
  ): Schedule.Aux[F, S, A, B] = new Schedule[F, A, B] {
    type State = S
    val initial = initial0
    val update  = update0
  }
}

trait Scheduler {
  import Schedule.Decision

  def run[F[_]: Monad, A, B](F: F[A], schedule: Schedule[F, A, B])(implicit timer: Timer[F]): F[B] = {
    def loop(decision: Decision[schedule.State, B]): F[B] =
      if (decision.continue)
        for {
          _ <- timer.sleep(decision.delay)
          a <- F
          d <- schedule.update(a, decision.state)
          b <- loop(d)
        } yield b
      else decision.result.pure[F]

    schedule.initial
      .flatMap(
        initial =>
          for {
            _ <- timer.sleep(initial.delay)
            a <- F
            d <- schedule.update(a, initial.state)
          } yield d
      )
      .flatMap(loop)
  }
}

trait ScheduleInstances {
  import Schedule.{Init, Decision}

  implicit def eqForInit[S: Eq] = new Eq[Init[S]] {
    def eqv(i1: Init[S], i2: Init[S]) =
      i1.delay == i2.delay &&
        i1.state === i2.state
  }

  implicit val functorForInit = new Functor[Init] {
    def map[A, B](init: Init[A])(f: A => B) = Init(init.delay, f(init.state))
  }

  implicit def eqForDecision[S: Eq, B: Eq] = new Eq[Decision[S, B]] {
    def eqv(d1: Decision[S, B], d2: Decision[S, B]) =
      d1.continue == d2.continue &&
        d1.delay == d2.delay &&
        d1.state === d2.state &&
        d1.result === d2.result
  }

  implicit val bifunctorForDecision = new Bifunctor[Decision] {
    def bimap[A, B, C, D](fab: Decision[A, B])(f: A => C, g: B => D): Decision[C, D] =
      fab.copy(state = f(fab.state), result = g(fab.result))
  }

  implicit def eqForSchedule[F[_], S, A, B](
      implicit eqFI: Eq[F[Init[S]]],
      eqASFD: Eq[(A, S) => F[Decision[S, B]]]
  ) = new Eq[Schedule.Aux[F, S, A, B]] {
    def eqv(s1: Schedule.Aux[F, S, A, B], s2: Schedule.Aux[F, S, A, B]) =
      s1.initial === s2.initial && s1.update === s2.update
  }

  implicit def profunctorForSchedule[F[_]: Functor, S] = new Profunctor[Schedule.Aux[F, S, ?, ?]] {
    def dimap[A, B, C, D](fab: Schedule.Aux[F, S, A, B])(f: C => A)(g: B => D): Schedule.Aux[F, S, C, D] =
      Schedule[F, S, C, D](
        fab.initial,
        (c, s) => fab.update(f(c), s).map(d => Decision(d.continue, d.delay, d.state, g(d.result)))
      )
  }
}

trait PredefinedSchedules {
  import Schedule.{Init, Decision}
  import syntax._

  def unfold[F[_]: Applicative, A, B](zero: => B)(f: B => B): Schedule.Aux[F, B, A, B] = Schedule(
    Init(0.millis, zero).pure,
    (_, b) => Decision(continue = true, 0.millis, f(b), f(b)).pure
  )

  def forever[F[_]: Applicative, A]: Schedule.Aux[F, Int, A, Int] =
    unfold(0)(_ + 1)

  def never[F[_]: Async, A]: Schedule[F, A, Nothing] = Schedule[F, Unit, A, Nothing](
    Async[F].never,
    (_, _) => Async[F].never
  )

  def occurs[F[_]: Applicative, A](times: Int): Schedule.Aux[F, Int, A, Int] =
    forever.reconsider(_.result < times)

  def after[F[_]: Applicative, A](delay: FiniteDuration): Schedule.Aux[F, Int, A, Int] =
    forever.after(delay)

  def spaced[F[_]: Applicative, A](interval: FiniteDuration): Schedule.Aux[F, Int, A, Int] =
    forever.delay(interval)
}

trait Combinators {
  import Schedule.{Init, Decision}

  def mapInit[F[_]: Functor, S, A, B](
      S: Schedule.Aux[F, S, A, B],
      f: Init[S] => Init[S]
  ): Schedule.Aux[F, S, A, B] =
    Schedule(
      S.initial.map(f),
      S.update
    )

  def mapDecision[F[_]: Functor, S, A, B](
      S: Schedule.Aux[F, S, A, B],
      f: Decision[S, B] => Decision[S, B]
  ): Schedule.Aux[F, S, A, B] =
    Schedule(
      S.initial,
      S.update(_, _).map(f)
    )

  def after[F[_]: Functor, S, A, B](
      S: Schedule.Aux[F, S, A, B],
      delay: FiniteDuration
  ): Schedule.Aux[F, S, A, B] =
    mapInit(S, _.copy(delay = delay))

  def delay[F[_]: Functor, S, A, B](
      S: Schedule.Aux[F, S, A, B],
      interval: FiniteDuration
  ): Schedule.Aux[F, S, A, B] =
    mapDecision(S, _.copy(delay = interval))

  def reconsider[F[_]: Functor, S, A, B](
      S: Schedule.Aux[F, S, A, B],
      f: Decision[S, B] => Boolean
  ): Schedule.Aux[F, S, A, B] =
    mapDecision(S, d => d.copy(continue = f(d)))
}
