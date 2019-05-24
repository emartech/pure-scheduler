package com.emarsys.scheduler

import cats.Monad
import cats.effect.IO
import cats.effect.laws.util.TestContext

trait IOScope {
  val ctx                   = TestContext()
  implicit val contextShift = IO.contextShift(ctx)
  implicit val timer        = ctx.timer[IO]
  implicit val M            = implicitly[Monad[IO]]
}
