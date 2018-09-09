package com.emarsys

package object scheduler {
  type FDur = scala.concurrent.duration.FiniteDuration

  object dsl extends DSL
}
