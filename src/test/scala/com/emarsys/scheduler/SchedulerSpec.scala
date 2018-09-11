package com.emarsys.scheduler

import cats.effect.IO
import cats.effect.concurrent.Ref
import org.scalatest.{WordSpec, Matchers}
import scala.concurrent.duration._

class SchedulerSpec extends WordSpec with Matchers {
  import Algebra._

  val globalEc              = scala.concurrent.ExecutionContext.global
  implicit val contextShift = IO.contextShift(globalEc)
  implicit val timer        = IO.timer(globalEc)

  trait ScheduleScope {
    val timeBox = 55.millis
    def schedule: Ref[IO, List[String]] => Schedule[IO, Unit]

    def io(id: String, ref: Ref[IO, List[String]]) =
      ref.modify(ids => (id :: ids, ()))

    def scheduled: IO[List[String]] =
      Ref.of[IO, List[String]](Nil) flatMap { ref =>
        dsl.run(schedule(ref)).timeoutTo(timeBox, IO.unit) flatMap (_ => ref.get)
      }
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

        scheduled.unsafeRunSync.sorted shouldEqual List("a", "b", "c")
      }
    }

    "scheduled with different delays" should {
      "run one after the other as specified" in new ScheduleScope {
        val schedule = ref =>
          for {
            _ <- dsl.now(io("a", ref))
            _ <- dsl.after(20.millis, io("b", ref))
            _ <- dsl.after(10.millis, io("c", ref))
          } yield ()

        scheduled.unsafeRunSync.reverse shouldEqual List("a", "c", "b")
      }
    }

    "scheduled on repeat" when {
      "the repeat starts immediately" should {
        "re-run in the given timeframe as many times as they can" in new ScheduleScope {
          val schedule = ref => dsl.repeat(io("R", ref), 15.millis)

          scheduled.unsafeRunSync shouldEqual List.fill(4)("R")
        }
      }

      "repeatAfter is used" should {
        "re-run in the given timeframe after the delay as many times as they can" in new ScheduleScope {
          val schedule = ref => dsl.repeatAfter(11.millis, io("R", ref), 15.millis)

          scheduled.unsafeRunSync shouldEqual List.fill(3)("R")
        }
      }
    }

    "scheduled" should {
      "run in parallel, starting at the same time" in new ScheduleScope {
        val schedule = ref =>
          for {
            _ <- dsl.repeat(io("repeat", ref), 15.millis)
            _ <- dsl.after(5.millis, io("5ms delay", ref))
            _ <- dsl.repeatAfter(10.millis, io("delayed repeat", ref), 15.millis)
          } yield ()

        scheduled.unsafeRunSync.reverse shouldEqual List(
          "repeat", // <- 0 ms
          "5ms delay", // <- 5 ms
          "delayed repeat", // <- 10 ms
          "repeat", // <- 15 ms
          "delayed repeat", // <- 25 ms
          "repeat", // <- 30 ms
          "delayed repeat", // <- 40 ms
          "repeat" // <- 45 ms
        )
      }
    }
  }
}
