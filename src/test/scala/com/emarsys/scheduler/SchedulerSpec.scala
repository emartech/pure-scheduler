package com.emarsys.scheduler

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.effect.laws.util.TestContext
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

class SchedulerSpec extends WordSpec with Matchers {
  import Algebra._

  val ctx                   = TestContext()
  implicit val contextShift = IO.contextShift(ctx)
  implicit val timer        = ctx.timer[IO]

  trait ScheduleScope {
    val timeBox = 5 seconds
    def schedule: Ref[IO, List[String]] => Schedule[IO, Unit]

    def io(id: String, ref: Ref[IO, List[String]]) =
      ref.modify(ids => (id :: ids, ()))

    def scheduled: IO[List[String]] =
      Ref.of[IO, List[String]](Nil) flatMap { ref =>
        dsl.run(schedule(ref)).timeoutTo(timeBox, IO.unit) flatMap (_ => ref.get)
      }

    lazy val runSchedule = {
      val f = scheduled.unsafeToFuture()
      ctx.tick(timeBox)
      f
    }

    def endState = runSchedule.value.fold[List[String]](Nil)(_.getOrElse(Nil))
  }

  "Referentially transparent effect types" when {
    "scheduled with now" should {
      "run in parallel" in new ScheduleScope {
        val schedule = ref =>
          for {
            _ <- dsl.now(io("a", ref))
            _ <- dsl.now(io("b", ref))
            _ <- dsl.now(io("c", ref))
          } yield ()

        runSchedule

        endState.sorted shouldEqual List("a", "b", "c")
      }
    }

    "scheduled with different delays" should {
      "run one after the other as specified" in new ScheduleScope {
        val schedule = ref =>
          for {
            _ <- dsl.now(io("a", ref))
            _ <- dsl.after(2 seconds, io("b", ref))
            _ <- dsl.after(1 seconds, io("c", ref))
          } yield ()

        runSchedule

        endState.reverse shouldEqual List("a", "c", "b")
      }
    }

    "scheduled on repeat" when {
      "the repeat starts immediately" should {
        "re-run in the given timeframe as many times as they can" in new ScheduleScope {
          val schedule = ref => dsl.repeat(io("R", ref), 1500 millis)

          runSchedule

          endState shouldEqual List.fill(4)("R")
        }
      }

      "repeatAfter is used" should {
        "re-run in the given timeframe after the delay as many times as they can" in new ScheduleScope {
          val schedule = ref => dsl.repeatAfter(1100 millis, io("R", ref), 1500 millis)

          runSchedule

          endState shouldEqual List.fill(3)("R")
        }
      }
    }

    "scheduled" should {
      "run in parallel, starting at the same time" in new ScheduleScope {
        val schedule = ref =>
          for {
            _ <- dsl.repeat(io("repeat", ref), 1500 millis)
            _ <- dsl.after(500 millis, io("500ms delay", ref))
            _ <- dsl.repeatAfter(1 second, io("delayed repeat", ref), 1500 millis)
          } yield ()

        runSchedule

        endState.reverse shouldEqual List(
          "repeat", // <- 0 ms
          "500ms delay", // <- 500 ms
          "delayed repeat", // <- 1000 ms
          "repeat", // <- 1500 ms
          "delayed repeat", // <- 2500 ms
          "repeat", // <- 3000 ms
          "delayed repeat", // <- 4000 ms
          "repeat" // <- 4500 ms
        )
      }
    }
  }
}
