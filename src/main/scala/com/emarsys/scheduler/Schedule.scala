package com.emarsys.scheduler

import cats.{Applicative, Apply, Bifunctor, Eq, Functor, Monad}
import cats.arrow.Profunctor
import cats.effect.{Async, Timer}
import cats.syntax.all._

import scala.concurrent.duration._

trait Schedule[F[+ _], -A, +B] {
  type State
  val initial: F[Schedule.Init[State]]
  val update: (A, State) => F[Schedule.Decision[State, B]]
}

object Schedule extends Scheduler with ScheduleInstances with PredefinedSchedules with Combinators {
  type Aux[F[+ _], S, A, B] = Schedule[F, A, B] { type State = S }

  final case class Init[S](delay: FiniteDuration, state: S)
  final case class Decision[S, +B](continue: Boolean, delay: FiniteDuration, state: S, result: B)

  def apply[F[+ _], S, A, B](
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

  def run[F[+ _]: Monad, A, B](F: F[A], schedule: Schedule[F, A, B])(implicit timer: Timer[F]): F[B] = {
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

  implicit def eqForSchedule[F[+ _], S, A, B](
      implicit eqFI: Eq[F[Init[S]]],
      eqASFD: Eq[(A, S) => F[Decision[S, B]]]
  ) = new Eq[Schedule.Aux[F, S, A, B]] {
    def eqv(s1: Schedule.Aux[F, S, A, B], s2: Schedule.Aux[F, S, A, B]) =
      s1.initial === s2.initial && s1.update === s2.update
  }

  implicit def profunctorForSchedule[F[+ _]: Functor, S] = new Profunctor[Schedule.Aux[F, S, ?, ?]] {
    def dimap[A, B, C, D](fab: Schedule.Aux[F, S, A, B])(f: C => A)(g: B => D): Schedule.Aux[F, S, C, D] =
      Schedule[F, S, C, D](
        fab.initial,
        (c, s) => fab.update(f(c), s).map(d => Decision(d.continue, d.delay, d.state, g(d.result)))
      )
  }

  implicit def relaxedProfunctorForSchedule[F[+ _]: Functor] = new Profunctor[Schedule[F, ?, ?]] {
    def dimap[A, B, C, D](fab: Schedule[F, A, B])(f: C => A)(g: B => D): Schedule[F, C, D] =
      profunctorForSchedule[F, fab.State].dimap(fab)(f)(g)
  }

  implicit def functorForSchedule[F[+ _]: Functor, S, A] = new Functor[Schedule.Aux[F, S, A, ?]] {
    def map[B, C](fa: Schedule.Aux[F, S, A, B])(f: B => C) = profunctorForSchedule[F, S].rmap(fa)(f)
  }

  implicit def relaxedFunctorForSchedule[F[+ _]: Functor, A] = new Functor[Schedule[F, A, ?]] {
    def map[B, C](fa: Schedule[F, A, B])(f: B => C) = functorForSchedule[F, fa.State, A].map(fa)(f)
  }
}

trait PredefinedSchedules {
  import Schedule.{Init, Decision}
  import syntax._

  def unfold[F[+ _]: Applicative, B](zero: => B)(f: B => B): Schedule[F, Any, B] = Schedule[F, B, Any, B](
    Init(0.millis, zero).pure[F],
    (_, b) => Decision(continue = true, 0.millis, f(b), f(b)).pure[F]
  )

  def forever[F[+ _]: Applicative]: Schedule[F, Any, Int] =
    unfold(0)(_ + 1)

  def never[F[+ _]: Async]: Schedule[F, Any, Nothing] = Schedule[F, Unit, Any, Nothing](
    Async[F].never,
    (_, _) => Async[F].never
  )

  def identity[F[+ _]: Applicative, A]: Schedule[F, A, A] = Schedule[F, Unit, A, A](
    Init(0.millis, ()).pure[F],
    (a, _) => Decision(continue = true, 0.millis, (), a).pure[F]
  )

  def occurs[F[+ _]: Applicative](times: Int): Schedule[F, Any, Int] =
    forever.reconsider(_.result < times)

  def after[F[+ _]: Applicative](delay: FiniteDuration): Schedule[F, Any, Int] =
    forever.after(delay)

  def spaced[F[+ _]: Applicative](interval: FiniteDuration): Schedule[F, Any, Int] =
    forever.space(interval)

  def continueOn[F[+ _]: Applicative](b: Boolean): Schedule[F, Boolean, Int] =
    forever <* identity.reconsider(_.result == b)

  def whileInput[F[+ _]: Applicative, A](p: A => Boolean): Schedule[F, A, Int] =
    continueOn(true) lmap p

  def untilInput[F[+ _]: Applicative, A](p: A => Boolean): Schedule[F, A, Int] =
    continueOn(false) lmap p
}

trait Combinators {
  import Schedule.{Init, Decision}

  type Combine[A] = (A, A) => A

  def combine[F[+ _]: Apply, A, B, C](S1: Schedule[F, A, B], S2: Schedule[F, A, C])(
      cont: Combine[Boolean]
  )(delay: Combine[FiniteDuration]): Schedule[F, A, (B, C)] =
    Schedule[F, (S1.State, S2.State), A, (B, C)](
      (S1.initial, S2.initial) mapN {
        case (Init(d1, s1), Init(d2, s2)) => Init(delay(d1, d2), (s1, s2))
      }, {
        case (a, (s1, s2)) =>
          (S1.update(a, s1), S2.update(a, s2)) mapN {
            case (Decision(c1, d1, s1, b), Decision(c2, d2, s2, c)) =>
              Decision(cont(c1, c2), delay(d1, d2), (s1, s2), (b, c))
          }
      }
    )

  def mapInit[F[+ _]: Functor, A, B](S: Schedule[F, A, B])(
      f: Init[S.State] => Init[S.State]
  ): Schedule[F, A, B] =
    Schedule[F, S.State, A, B](
      S.initial.map(f),
      S.update
    )

  def mapDecision[F[+ _]: Functor, A, B](S: Schedule[F, A, B])(
      f: Decision[S.State, B] => Decision[S.State, B]
  ): Schedule[F, A, B] =
    Schedule[F, S.State, A, B](
      S.initial,
      S.update(_, _).map(f)
    )

  def after[F[+ _]: Functor, A, B](
      S: Schedule[F, A, B],
      delay: FiniteDuration
  ): Schedule[F, A, B] =
    mapInit(S)(_.copy(delay = delay))

  def space[F[+ _]: Functor, A, B](
      S: Schedule[F, A, B],
      interval: FiniteDuration
  ): Schedule[F, A, B] =
    mapDecision(S)(_.copy(delay = interval))

  def reconsider[F[+ _]: Functor, A, B](S: Schedule[F, A, B])(f: Decision[S.State, B] => Boolean): Schedule[F, A, B] =
    mapDecision(S)(d => d.copy(continue = f(d)))

  def fold[F[+ _]: Functor, A, B, Z](S: Schedule[F, A, B])(z: Z)(c: (Z, B) => Z): Schedule[F, A, Z] =
    Schedule[F, (Z, S.State), A, Z](
      S.initial.map(i => Init(i.delay, (z, i.state))), {
        case (a, (z, s)) =>
          S.update(a, s) map {
            case Decision(cont, delay, state, b) =>
              val z2 = c(z, b)
              Decision(cont, delay, (z2, state), z2)
          }
      }
    )
}
