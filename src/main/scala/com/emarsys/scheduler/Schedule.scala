package com.emarsys.scheduler

import cats.{Applicative, Apply, Bifunctor, Eq, Functor, Monad, MonadError}
import cats.arrow.Profunctor
import cats.effect.{Async, Timer}
import cats.syntax.all._

import scala.concurrent.duration._

trait Schedule[F[+_], -A, +B] {
  type State
  val initial: F[Schedule.Init[State]]
  val update: (A, State) => F[Schedule.Decision[State, B]]
}

object Schedule extends Scheduler with ScheduleInstances with PredefinedSchedules with Combinators {
  type Aux[F[+_], S, A, B] = Schedule[F, A, B] { type State = S }
  type Combine[A]          = (A, A) => A

  final case class Init[S](delay: FiniteDuration, state: S) {
    def combineWith[S2](that: Init[S2])(combD: Combine[FiniteDuration]) =
      Init(combD(delay, that.delay), (state, that.state))
  }
  final case class Decision[S, +B](continue: Boolean, delay: FiniteDuration, state: S, result: B) {
    def combineWith[S2, B2](that: Decision[S2, B2])(cont: Combine[Boolean])(combD: Combine[FiniteDuration]) =
      Decision(cont(continue, that.continue), combD(delay, that.delay), (state, that.state), (result, that.result))
  }

  def apply[F[+_], S, A, B](
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

  def run[F[+_]: Monad: Timer, A, B](F: F[A], schedule: Schedule[F, A, B]): F[B] = {
    def loop(decision: Decision[schedule.State, B]): F[B] =
      if (decision.continue)
        for {
          _ <- Timer[F].sleep(decision.delay)
          a <- F
          d <- schedule.update(a, decision.state)
          b <- loop(d)
        } yield b
      else decision.result.pure[F]

    schedule.initial
      .flatMap(
        initial =>
          for {
            _ <- Timer[F].sleep(initial.delay)
            a <- F
            d <- schedule.update(a, initial.state)
          } yield d
      )
      .flatMap(loop)
  }

  def retry[E, F[+_]: MonadError[?[_], E]: Timer, A, B](F: F[A], policy: Schedule[F, E, B]): F[A] = {
    def loop(decision: Decision[policy.State, B]): PartialFunction[E, F[A]] = {
      case e if decision.continue =>
        for {
          _    <- Timer[F].sleep(decision.delay)
          next <- policy.update(e, decision.state)
          a    <- F.recoverWith(loop(next))
        } yield a
    }

    F recoverWith {
      case e =>
        for {
          initial <- policy.initial
          _       <- Timer[F].sleep(initial.delay)
          d       <- policy.update(e, initial.state)
          a       <- F.recoverWith(loop(d))
        } yield a
    }
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

  implicit def eqForSchedule[F[+_], S, A, B](
      implicit eqFI: Eq[F[Init[S]]],
      eqASFD: Eq[(A, S) => F[Decision[S, B]]]
  ) = new Eq[Schedule.Aux[F, S, A, B]] {
    def eqv(s1: Schedule.Aux[F, S, A, B], s2: Schedule.Aux[F, S, A, B]) =
      s1.initial === s2.initial && s1.update === s2.update
  }

  implicit def profunctorForSchedule[F[+_]: Functor, S] = new Profunctor[Schedule.Aux[F, S, ?, ?]] {
    def dimap[A, B, C, D](fab: Schedule.Aux[F, S, A, B])(f: C => A)(g: B => D): Schedule.Aux[F, S, C, D] =
      Schedule[F, S, C, D](
        fab.initial,
        (c, s) => fab.update(f(c), s).map(d => Decision(d.continue, d.delay, d.state, g(d.result)))
      )
  }

  implicit def relaxedProfunctorForSchedule[F[+_]: Functor] = new Profunctor[Schedule[F, ?, ?]] {
    def dimap[A, B, C, D](fab: Schedule[F, A, B])(f: C => A)(g: B => D): Schedule[F, C, D] =
      profunctorForSchedule[F, fab.State].dimap(fab)(f)(g)
  }

  implicit def functorForSchedule[F[+_]: Functor, S, A] = new Functor[Schedule.Aux[F, S, A, ?]] {
    def map[B, C](fa: Schedule.Aux[F, S, A, B])(f: B => C) = profunctorForSchedule[F, S].rmap(fa)(f)
  }

  implicit def relaxedFunctorForSchedule[F[+_]: Functor, A] = new Functor[Schedule[F, A, ?]] {
    def map[B, C](fa: Schedule[F, A, B])(f: B => C) = functorForSchedule[F, fa.State, A].map(fa)(f)
  }
}

trait PredefinedSchedules {
  import Schedule.{Init, Decision}
  import syntax._

  def unfold[F[+_]: Applicative, B](zero: => B)(f: B => B): Schedule.Aux[F, B, Any, B] = Schedule[F, B, Any, B](
    Init(Duration.Zero, zero).pure[F],
    (_, b) => Decision(continue = true, Duration.Zero, f(b), f(b)).pure[F]
  )

  def unfoldM[F[+_]: Functor, B](zero: F[B])(f: B => F[B]): Schedule.Aux[F, B, Any, B] = Schedule[F, B, Any, B](
    zero.map(z => Init(Duration.Zero, z)),
    (_, b) => f(b).map(newB => Decision(continue = true, Duration.Zero, newB, newB))
  )

  def forever[F[+_]: Applicative]: Schedule[F, Any, Int] =
    unfold(0)(_ + 1)

  def never[F[+_]: Async]: Schedule[F, Any, Nothing] = Schedule[F, Unit, Any, Nothing](
    Async[F].never,
    (_, _) => Async[F].never
  )

  def identity[F[+_]: Applicative, A]: Schedule[F, A, A] = Schedule[F, Unit, A, A](
    Init(Duration.Zero, ()).pure[F],
    (a, _) => Decision(continue = true, Duration.Zero, (), a).pure[F]
  )

  def occurs[F[+_]: Monad](times: Int): Schedule[F, Any, Int] =
    forever.reconsider(_.result < times)

  def after[F[+_]: Monad](delay: FiniteDuration): Schedule[F, Any, Int] =
    forever.after(delay)

  def spaced[F[+_]: Monad](interval: FiniteDuration): Schedule[F, Any, Int] =
    forever.space(interval)

  def fixed[F[+_]: Monad: Timer](interval: FiniteDuration): Schedule[F, Any, FiniteDuration] =
    Schedule.mapDecision(elapsed.map(elapsed => (interval - elapsed) max Duration.Zero)) { d =>
      val (_, n) = d.state
      d.copy(
        state = (d.result.toNanos + n, n),
        delay = d.result
      )
    }

  def elapsed[F[+_]: Functor: Timer]: Schedule.Aux[F, (Long, Long), Any, FiniteDuration] =
    timing.map { case (s, n) => (n - s).nanos }

  def timing[F[+_]: Functor: Timer]: Schedule.Aux[F, (Long, Long), Any, (Long, Long)] =
    unfoldM(Timer[F].clock.monotonic(NANOSECONDS).map(n => (n, n))) {
      case (s, _) => Timer[F].clock.monotonic(NANOSECONDS).map(n => (s, n))
    }

  def maxFor[F[+_]: Monad: Timer](timeCap: FiniteDuration): Schedule[F, Any, FiniteDuration] =
    elapsed.reconsider(_.result < timeCap)

  def continueOn[F[+_]: Monad](b: Boolean): Schedule[F, Boolean, Int] =
    forever <* identity.reconsider(_.result == b)

  def whileInput[F[+_]: Monad, A](p: A => Boolean): Schedule[F, A, Int] =
    continueOn(true) lmap p

  def untilInput[F[+_]: Monad, A](p: A => Boolean): Schedule[F, A, Int] =
    continueOn(false) lmap p

  def collect[F[+_]: Monad, A]: Schedule[F, A, List[A]] =
    identity.collect

  def fibonacci[F[+_]: Applicative](one: FiniteDuration): Schedule[F, Any, FiniteDuration] =
    Schedule.delayFromOut(unfold((Duration.Zero, one))({ case (p, c) => (c, p + c) }).map(_._2))

  def linear[F[+_]: Applicative](unit: FiniteDuration): Schedule[F, Any, FiniteDuration] =
    Schedule.delayFromOut(forever.map(_ * unit))

  def exponential[F[+_]: Applicative](unit: FiniteDuration, base: Double = 2.0): Schedule[F, Any, FiniteDuration] =
    Schedule.delayFromOut(forever.map(exponent => unit * math.pow(base, exponent.doubleValue).longValue))
}

trait Combinators {
  import Schedule.{Init, Decision, Combine}

  def combine[F[+_]: Apply, A, A1 <: A, B, C](S1: Schedule[F, A, B], S2: Schedule[F, A1, C])(
      cont: Combine[Boolean]
  )(delay: Combine[FiniteDuration]): Schedule[F, A1, (B, C)] =
    Schedule[F, (S1.State, S2.State), A1, (B, C)](
      (S1.initial, S2.initial) mapN {
        case (i1, i2) => i1.combineWith(i2)(delay)
      }, {
        case (a, (s1, s2)) =>
          (S1.update(a, s1), S2.update(a, s2)) mapN {
            case (d1, d2) => d1.combineWith(d2)(cont)(delay)
          }
      }
    )

  def mapInit[F[+_]: Functor, A, B](S: Schedule[F, A, B])(
      f: Init[S.State] => Init[S.State]
  ): Schedule[F, A, B] =
    Schedule[F, S.State, A, B](
      S.initial.map(f),
      S.update
    )

  def mapDecision[F[+_]: Functor, A, B](S: Schedule[F, A, B])(
      f: Decision[S.State, B] => Decision[S.State, B]
  ): Schedule[F, A, B] =
    Schedule[F, S.State, A, B](
      S.initial,
      S.update(_, _).map(f)
    )

  def delayFromOut[F[+_]: Functor, A](S: Schedule[F, A, FiniteDuration]) =
    mapDecision(S)(d => d.copy(delay = d.result))

  def after[F[+_]: Functor, A, B](
      S: Schedule[F, A, B],
      delay: FiniteDuration
  ): Schedule[F, A, B] =
    mapInit(S)(_.copy(delay = delay))

  def space[F[+_]: Functor, A, B](
      S: Schedule[F, A, B],
      interval: FiniteDuration
  ): Schedule[F, A, B] =
    mapDecision(S)(_.copy(delay = interval))

  def reconsider[F[+_]: Functor, A, B](S: Schedule[F, A, B])(f: Decision[S.State, B] => Boolean): Schedule[F, A, B] =
    mapDecision(S)(d => d.copy(continue = f(d)))

  def fold[F[+_]: Functor, A, B, Z](S: Schedule[F, A, B])(z: Z)(c: (Z, B) => Z): Schedule[F, A, Z] =
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

  def sequence[F[+_]: Monad, A, B, C](S1: Schedule[F, A, B], S2: Schedule[F, A, C]) = {
    def first(a: A, s1: S1.State): F[Decision[Either[S1.State, S2.State], Either[B, C]]] =
      S1.update(a, s1).map(_.bimap(Left(_), Left(_)))

    def second(a: A, s2: S2.State): F[Decision[Either[S1.State, S2.State], Either[B, C]]] =
      S2.update(a, s2).map(_.bimap(Right(_), Right(_)))

    Schedule[F, Either[S1.State, S2.State], A, Either[B, C]](
      S1.initial.map(_.map(Left(_))), {
        case (a, Left(s1)) =>
          first(a, s1) flatMap { d =>
            if (d.continue) d.pure[F]
            else S2.initial.map(i => Decision(continue = true, i.delay, Right(i.state), d.result))
          }
        case (a, Right(s2)) => second(a, s2)
      }
    )
  }

  def compose[F[+_]: Monad, A, B, C](S1: Schedule[F, A, B], S2: Schedule[F, B, C]): Schedule[F, A, C] =
    Schedule[F, (S1.State, S2.State), A, C](
      for {
        i1 <- S1.initial
        i2 <- S2.initial
      } yield i1.combineWith(i2)(_ + _), {
        case (a, (s1, s2)) =>
          for {
            d1 <- S1.update(a, s1)
            d2 <- S2.update(d1.result, s2)
          } yield d1.combineWith(d2)(_ && _)(_ + _).bimap(identity, _._2)
      }
    )

  def onDecision[F[+_]: Monad, A, B](S: Schedule[F, A, B])(
      f: Decision[S.State, B] => F[Unit]
  ): Schedule[F, A, B] =
    Schedule[F, S.State, A, B](
      S.initial,
      (a, s) =>
        for {
          d <- S.update(a, s)
          _ <- f(d)
        } yield d
    )
}
