package com.emarsys.scheduler

import cats.Monad
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.effect.laws.util.TestContext
import org.scalatest.{Assertions, Matchers, WordSpec}

import scala.concurrent.duration._
import scala.util._

class SchedulerSpec extends WordSpec with Assertions with Matchers {
  import syntax._
  import cats.syntax.functor._

  val ctx                   = TestContext()
  implicit val contextShift = IO.contextShift(ctx)
  implicit val timer        = ctx.timer[IO]

  trait ScheduleScope {
    type Out
    val timeBox = 5 seconds
    val program: IO[Out]
    implicit val M = implicitly[Monad[IO]]

    lazy val runProgram = {
      val f = program.unsafeToFuture
      ctx.tick(timeBox)
      f
    }

    lazy val endState = runProgram.value match {
      case None                 => fail(s"Scheduled program have not completed in $timeBox")
      case Some(Failure(e))     => fail(e.toString, e)
      case Some(Success(state)) => state
    }
  }

  trait RunTimesScope extends ScheduleScope {
    type Out = (Long, List[Long])

    def schedule: Schedule[IO, Any, Any]

    val collectRunTimes: Ref[IO, List[Long]] => IO[Unit] = { ref =>
      for {
        current <- timer.clock.realTime(SECONDS)
        _       <- ref.modify(ts => (current :: ts, ()))
      } yield ()
    }

    val program = for {
      ref      <- Ref.of[IO, List[Long]](Nil)
      start    <- timer.clock.realTime(SECONDS)
      _        <- collectRunTimes(ref).runOn(schedule).timeoutTo(timeBox, IO.unit)
      runTimes <- ref.get
    } yield (start, runTimes)

    lazy val (start, runTimes)          = endState
    lazy val differencesBetweenRunTimes = runTimes.zip(runTimes.tail) map { case (c, p) => c - p }

    def startedImmediately = {
      runTimes should not be empty
      runTimes.last shouldEqual start
    }
  }

  "A recurring schedule" should {
    "recur as specified and return the number of occurences" in new ScheduleScope {
      type Out = Int
      val program = IO(100).runOn(Schedule.occurs(5))

      endState shouldEqual 5
    }
  }

  "An initially delayed schedule" should {
    "only start after the specified delay" in new ScheduleScope {
      type Out = (Long, Long)

      val program = for {
        start <- timer.clock.realTime(SECONDS)
        _     <- IO(100).runOn(Schedule.occurs(1).after(1.second))
        end   <- timer.clock.realTime(SECONDS)
      } yield (start, end)

      val (start, end) = endState
      end - start shouldEqual 1
    }
  }

  "A spaced schedule" when {
    "defined via the `spaced` predefined schedule" should {
      "start immediately and run with the specified fixed delay afterwards" in new RunTimesScope {
        val schedule = Schedule.spaced(1.second)

        startedImmediately
        differencesBetweenRunTimes.forall(_ == 1) shouldBe true
      }
    }

    "defined via the `space`-ing an existing schedule" should {
      "start immediately and run with the specified fixed delay afterwards" in new RunTimesScope {
        val schedule = Schedule.occurs(2).space(1.second)

        startedImmediately
        differencesBetweenRunTimes.forall(_ == 1) shouldBe true
      }
    }
  }

  "A combination of two schedules" when {
    "combined with AND" should {
      "continue when both of the schedules continue" in new ScheduleScope {
        type Out = (Int, Int)

        val program = IO(100).runOn(Schedule.forever && Schedule.occurs(1))

        endState shouldEqual ((1, 1))
      }

      "use the maximum of the delays for init" in new ScheduleScope {
        type Out = (Long, Long)

        val program = for {
          start <- timer.clock.realTime(SECONDS)
          _     <- IO(100).runOn(Schedule.occurs(1).after(1.second) && Schedule.occurs(1))
          end   <- timer.clock.realTime(SECONDS)
        } yield (start, end)

        val (start, end) = endState
        end - start shouldEqual 1
      }

      "use the maximum of the delays for update" in new RunTimesScope {
        val schedule = Schedule.occurs(2) && Schedule.spaced(1.second)

        differencesBetweenRunTimes.forall(_ == 1) shouldBe true
      }
    }

    "combined with OR" should {
      "continue when at least one of the schedules continue" in new ScheduleScope {
        type Out = (Int, Int)

        val program = IO(100).runOn(Schedule.occurs(2) || Schedule.occurs(1))

        endState shouldEqual ((2, 2))
      }

      "use the minimum of the delays for init" in new ScheduleScope {
        type Out = (Long, Long)

        val program = for {
          start <- timer.clock.realTime(SECONDS)
          _     <- IO(100).runOn(Schedule.occurs(1).after(2.second) || Schedule.occurs(1).after(1.second))
          end   <- timer.clock.realTime(SECONDS)
        } yield (start, end)

        val (start, end) = endState
        end - start shouldEqual 1
      }

      "use the minimum of the delays for update" in new RunTimesScope {
        val schedule = Schedule.occurs(2).space(2.second) || Schedule.occurs(2).space(1.second)

        differencesBetweenRunTimes.forall(_ == 1) shouldBe true
      }
    }

    "combined with <*" should {
      "work as AND but keep only the left output" in new ScheduleScope {
        type Out = Int

        val program = IO(100).runOn(Schedule.forever <* Schedule.occurs(1).map(_.toString))

        endState shouldEqual 1
      }
    }

    "combined with *>" should {
      "work as AND but keep only the right output" in new ScheduleScope {
        type Out = String

        val program = IO(100).runOn(Schedule.forever *> Schedule.occurs(1).map(_.toString))

        endState shouldEqual "1"
      }
    }

    "combined with andAfterThat" should {
      "go through the first one, then the other" in new ScheduleScope {
        type Out = List[Int]

        val program = IO(100).runOn((Schedule.occurs(2) andAfterThat Schedule.occurs(3)).collect)

        endState shouldEqual List(1, 2, 1, 2, 3)
      }
    }
  }

  "The identity schedule" should {
    "output the value from the effect" in new ScheduleScope {
      type Out = Int

      val program = IO(100).runOn(Schedule.occurs(1) *> Schedule.identity)

      endState shouldEqual 100
    }
  }

  "The continueOn schedule" should {
    "go on as long as the effect returns the fixed boolean and return the occurences" in new ScheduleScope {
      type Out = Int

      val program = for {
        ref        <- Ref.of[IO, Boolean](false)
        occurences <- ref.modify(b => (!b, b)).runOn(Schedule.continueOn(false))
      } yield occurences

      endState shouldEqual 2
    }
  }

  "A schedule created with whileInput" should {
    "go on as long as the value from the effect satisfies the predicate" in new ScheduleScope {
      type Out = Int

      val program = for {
        ref        <- Ref.of[IO, Int](10)
        occurences <- ref.modify(x => (x + 10, x)).runOn(Schedule.whileInput(_ < 100))
      } yield occurences

      endState shouldEqual 10
    }
  }

  "A schedule created with untilInput" should {
    "go on as long as the value from the effect does not satisfy the predicate" in new ScheduleScope {
      type Out = Int

      val program = for {
        ref        <- Ref.of[IO, Int](10)
        occurences <- ref.modify(x => (x + 10, x)).runOn(Schedule.untilInput(_ > 100))
      } yield occurences

      endState shouldEqual 11
    }
  }

  "The fold combinator" should {
    "be able to fold over the output of any schedule" in new ScheduleScope {
      type Out = String

      val program = IO(1).runOn(Schedule.occurs(5).fold("")(_ + _.toString))

      endState shouldEqual "12345"
    }
  }

  "The collect combinator" should {
    "collect the outputs of a schedule" in new ScheduleScope {
      type Out = List[Int]

      val program = IO(1).runOn(Schedule.occurs(5).collect)

      endState shouldEqual List(1, 2, 3, 4, 5)
    }
  }

  "The collect schedule" should {
    "collect the values returned by the effect" in new ScheduleScope {
      type Out = List[Int]

      val program = for {
        ref <- Ref.of[IO, Int](10)
        out <- ref.modify(x => (x + 10, x)).runOn(Schedule.occurs(3) *> Schedule.collect)
      } yield out

      endState shouldEqual List(10, 20, 30)
    }
  }
}
