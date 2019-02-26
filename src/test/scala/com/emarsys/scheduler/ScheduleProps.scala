package com.emarsys.scheduler

import cats.{Applicative, Eq}
import cats.tests.CatsSuite
import cats.kernel.laws.discipline.EqTests
import cats.laws.discipline.{BifunctorTests, FunctorTests, ProfunctorTests}
import org.scalacheck.{Arbitrary, Cogen}
import Arbitrary.arbitrary
import Schedule.{Init, Decision}

import scala.concurrent.duration.FiniteDuration

class ScheduleProps extends CatsSuite with ScheduleGenerators with ArrowEq {
  checkAll(
    "Init.EqLaws",
    EqTests[Init[Int]].eqv
  )

  checkAll(
    "Init.FunctorLaws",
    FunctorTests[Init].functor[Int, Int, String]
  )

  checkAll(
    "Decision.EqLaws",
    EqTests[Decision[Int, String]].eqv
  )

  checkAll(
    "Decision.BifunctorLaws",
    BifunctorTests[Decision].bifunctor[Int, Boolean, String, Unit, String, Int]
  )

  checkAll(
    "Schedule.EqLaws",
    EqTests[Schedule.Aux[List, Unit, Int, String]].eqv
  )

  checkAll(
    "Schedule.ProfunctorLaws",
    ProfunctorTests[Schedule.Aux[List, Int, ?, ?]].profunctor[Unit, Int, Boolean, Int, Int, Int]
  )
}

trait ScheduleGenerators {
  import cats.syntax.applicative._

  implicit def genInit[S: Arbitrary]: Arbitrary[Init[S]] = Arbitrary(
    for {
      d <- arbitrary[FiniteDuration]
      s <- arbitrary[S]
    } yield Init(d, s)
  )

  implicit def cogenInit[S: Cogen]: Cogen[Init[S]] =
    Cogen((seed, i) => Cogen[S].perturb(seed, i.state))

  implicit def genDecision[S: Arbitrary, B: Arbitrary]: Arbitrary[Decision[S, B]] = Arbitrary(
    for {
      c <- arbitrary[Boolean]
      d <- arbitrary[FiniteDuration]
      s <- arbitrary[S]
      b <- arbitrary[B]
    } yield Decision(c, d, s, b)
  )

  implicit def cogenDecision[S: Cogen, B]: Cogen[Decision[S, B]] =
    Cogen((seed, d) => Cogen[S].perturb(seed, d.state))

  implicit def genSchedule[F[_]: Applicative, S: Arbitrary, A, B: Arbitrary]: Arbitrary[Schedule.Aux[F, S, A, B]] =
    Arbitrary(
      for {
        i <- arbitrary[Init[S]]
        d <- arbitrary[Decision[S, B]]
      } yield Schedule[F, S, A, B](i.pure, (_, _) => d.pure)
    )

  implicit def cogenSchedule[F[_], S, A, B](implicit C: Cogen[F[Init[S]]]): Cogen[Schedule.Aux[F, S, A, B]] =
    Cogen((seed, s) => C.perturb(seed, s.initial))
}

trait ArrowEq {
  import cats.syntax.eq._

  implicit def arrow2Eq[A: Arbitrary, B: Arbitrary, C: Eq]: Eq[(A, B) => C] = new Eq[(A, B) => C] {
    def eqv(f1: (A, B) => C, f2: (A, B) => C): Boolean = {
      List.fill(50)(arbitrary[A].sample zip arbitrary[B].sample).flatten forall {
        case (a, b) => f1(a, b) === f2(a, b)
      }
    }
  }
}
