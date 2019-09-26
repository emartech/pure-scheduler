package com.emarsys.scheduler

import cats.effect.IO
import cats.effect.concurrent.Ref
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.util._

class RetrySpec extends AnyWordSpec with Matchers {
  import Schedule.Decision
  import syntax._
  import cats.syntax.all._

  case class Timestamps(eval: Long, decision: Long) {
    def diff = decision - eval
  }

  val failingIO                 = IO.raiseError(new Exception)
  val fail: Unit => IO[Nothing] = _ => failingIO
  val unit: PartialFunction[Throwable, IO[Unit]] = {
    case _ => IO.unit
  }

  trait RetryScope extends IOScope {
    type Result

    val timeBox = 5 seconds
    val policy: Schedule[IO, Throwable, Result]
    lazy val failure: IO[Nothing] = failingIO

    def collect[S](
        evalsRef: Ref[IO, List[Long]],
        timestampsRef: Ref[IO, List[Timestamps]]
    ): Decision[S, Result] => IO[Unit] =
      _ =>
        for {
          evals <- evalsRef.get
          now   <- timer.clock.realTime(SECONDS)
          _     <- timestampsRef.modify(ts => (Timestamps(evals.head, now) :: ts, ()))
        } yield ()

    def recordEvaluation(ref: Ref[IO, List[Long]]): IO[Unit] =
      for {
        evalTime <- timer.clock.realTime(SECONDS)
        _        <- ref.modify(ets => (evalTime :: ets, ()))
      } yield ()

    lazy val program = for {
      evalsRef      <- Ref.of[IO, List[Long]](Nil)
      timestampsRef <- Ref.of[IO, List[Timestamps]](Nil)
      _ <- recordEvaluation(evalsRef)
        .flatMap(_ => failure)
        .retry(policy.onDecision(collect(evalsRef, timestampsRef)))
        .recoverWith(unit)
      timestamps <- timestampsRef.get
    } yield timestamps.reverse

    lazy val runProgram = {
      val f = program.unsafeToFuture
      ctx.tick(timeBox)
      f
    }

    lazy val timestamps = runProgram.value match {
      case None              => fail(s"Retrying of the program has not completed in $timeBox")
      case Some(Failure(e))  => fail(e.toString, e)
      case Some(Success(ts)) => ts
    }

    lazy val retryCount = timestamps.length

    lazy val secondsBetweenEvaluationsAndDecisions = timestamps.map(_.diff)
  }

  "Retrying with Schedule.occurs" should {
    "retry the effect the specified times" in new RetryScope {
      type Result = Int
      val policy = Schedule.occurs(3)

      retryCount shouldBe 3
    }

    "retry with no delay" in new RetryScope {
      type Result = Int
      val policy = Schedule.occurs(3)

      secondsBetweenEvaluationsAndDecisions shouldBe List(0, 0, 0)
    }
  }

  "Retrying once after an initial delay" should {
    "respect the initial delay" in new RetryScope {
      type Result = Int
      val policy = Schedule.occurs(1).after(1.second)

      secondsBetweenEvaluationsAndDecisions.head shouldBe 1
    }
  }

  "Retrying with a spaced schedule" should {
    "respect delays between retries" in new RetryScope {
      type Result = Int
      val policy = Schedule.spaced(2.second) *> Schedule.occurs(3)

      secondsBetweenEvaluationsAndDecisions shouldBe List(0, 2, 2)
    }
  }

  "Retrying with a fibonacci schedule" should {
    "apply increasing delays according to the fibonacci sequence" in new RetryScope {
      override val timeBox = 20 seconds
      type Result = Int
      val policy = Schedule.fibonacci(one = 1.second) *> Schedule.occurs(6)

      secondsBetweenEvaluationsAndDecisions shouldBe List(0, 1, 2, 3, 5, 8)
    }
  }

  "Retrying with a linear schedule" should {
    "apply linearly increasing delays" in new RetryScope {
      override val timeBox = 30 seconds
      type Result = Int
      val policy = Schedule.linear(unit = 2.second) *> Schedule.occurs(6)

      secondsBetweenEvaluationsAndDecisions shouldBe List(0, 2, 4, 6, 8, 10)
    }
  }

  "Retrying with an exponential schedule" should {
    "apply exponentially increasing delays" in new RetryScope {
      override val timeBox = 64 seconds
      type Result = Int
      val policy = Schedule.exponential(unit = 1.second, base = 2.0) *> Schedule.occurs(6)

      secondsBetweenEvaluationsAndDecisions shouldBe List(0, 2, 4, 8, 16, 32)
    }
  }
}
