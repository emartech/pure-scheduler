package com.emarsys.scheduler

import cats.Eval
import cats.effect.{Clock, Timer}
import org.scalatest.{WordSpec, Matchers}
import scala.concurrent.duration._

class InterpreterSpec extends WordSpec with Matchers {
  import Interpreter._

  val unit = Eval.now(())

  implicit val evalTimer = new Timer[Eval] {
    def clock = new Clock[Eval] {
      def monotonic(unit: TimeUnit) = Eval.now(1L)
      def realTime(unit: TimeUnit)  = Eval.now(2L)
    }
    def sleep(d: FiniteDuration) = unit
  }

  "interpreter" when {
    "given an F with Monad and Timer" should {
      "collect those F-s in a State" in {
        val program = for {
          _ <- dsl.now(unit)
          _ <- dsl.after(1.second, unit)
        } yield ()

        program
          .foldMap(interpret)
          .runS(Nil)
          .value
          .map(_.value) shouldEqual List((), ())
      }
    }
  }
}
