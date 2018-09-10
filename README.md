[![Build Status](https://travis-ci.org/emartech/pure-scheduler.svg?branch=master)](https://travis-ci.org/emartech/pure-scheduler)

# pure-scheduler

A pure scheduler for referentially transparent effect types.

## DSL

Building up the schedule is done with `cats.free.Free`. There are some smart constructors available in the `com.emarsys.scheduler.dsl` object.

```scala
def now[F[_]](F: F[Unit])
def after[F[_]](delay: FiniteDuration, F: F[Unit])
def repeat[F[_]](F: F[Unit], interval: FiniteDuration)
def repeatAfter[F[_]](delay: FiniteDuration, F: F[Unit], interval: FiniteDuration)
```

The results are instances of `Free`, they fit nicely in a `for` comprehension:

```scala
import com.emarsys.scheduler.dsl._
import scala.concurrent.duration._

val schedule = for {
  _ <- now(logStart)
  _ <- repeatAfter(10.minutes, doSomeWorkEveryHalfAnHourWithSomeInitialDelay, 30.minutes)
  _ <- repeat(doWork, 5.minutes)
  _ <- after(2.hours, shutdown)
} yield ()
```

These effects, when run, start all at once in parallel, despite being composed this way.
Building a schedule works with any `F[_]`. Running it, however, puts some constraints on it:

```scala
def run[F[_]: Monad: Timer, G[_], A](schedule: Schedule[F, A])(implicit P: Parallel[F, G]): F[Unit]
```

It is also important for the effect type to be lazy and referentially transparent: repeating won't work with, say, `Future`.

If we have picked `cats.effect.IO`, for example, then the following imports and implicits are required to run our example schedule.

```scala
import cats.effect.IO

val ec = scala.concurrent.ExecutionContext.global
implicit val timer = IO.timer(ec)
// The ContextShift instance is needed for deriving a `Parallel` instance via IO.ioParallel
implicit val contextShift = IO.contextShift(ec)

val result: IO[Unit] = run(schedule)
```
