package com.emarsys.scheduler

import cats.Monad
import cats.effect.IO

trait IOScope {
  implicit val M = implicitly[Monad[IO]]
}
