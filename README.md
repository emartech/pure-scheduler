# pure-scheduler [![Build Status](https://travis-ci.org/emartech/pure-scheduler.svg?branch=master)](https://travis-ci.org/emartech/pure-scheduler) [![Maven Central](https://img.shields.io/maven-central/v/com.emarsys/scheduler_2.12.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.emarsys%22%20AND%20a:%22scheduler_2.12%22)

A pure scheduler for _referentially transparent_ effect types.

This is in a very early stage of development, API-s may change. The current implementation was heavily inspired by the awesome [ZIO Schedule]. The idea is that one can build up arbitrarily complex strategies - aka. schedules - and use them both for repeating effects or retrying them. There are only a few predefined schedules and a handful of combinators to do this.

The aims of this library are

* work smoothly with tagless final
* have a cool DSL
* aid type inference as much as possible

## Include in your project

```
libraryDependencies += "com.emarsys" % "scheduler" % "x.y.z"
```

The latest released version is on the maven badge at the top of this document.

## The Schedule type

```scala
trait Schedule[F[+_], -A, +B] {
  type State
  val initial: F[Schedule.Init[State]]
  val update: (A, State) => F[Schedule.Decision[State, B]]
}
```

A schedule is a data structure that defines how repetition - or retrying - should be done. It defines two things: `initial` can tell if there should be an initial delay, and what the initial state is, `update` can make a decision based on a value of type `A` and the state. A decision carries the information whether the repetition should continue, what delay is to be applied, what is the new state and what is the current result (a value of type `B`).

### Repeating effects

A schedule of type `Schedule[F, A, B]` can be used to repeat effects of type `F[A]` producing a value of type `B` in the same context. A function for repeating effects are provided as an extension method for suitable `F`-s (having a `cats.Monad` and a `cats.effect.Timer` instance for them).

```scala
import com.emarsys.scheduler.Schedule
import com.emarsys.scheduler.syntax._

val effect: F[A]
val schedule: Schedule[F, A, B]

val scheduled: F[B] = effect runOn schedule
```

### Retrying effects

A schedule of type `Schedule[F, E, B]` can be used to retry effects of type `F[A]` that can fail with an error of type `E`. A function for repeating effects are provided as an extension method for suitable `F`-s (having a `cats.Moderror[?[_], E]` and a `cats.effect.Timer` instance for them).

> Note, that this `E` in practice will most likely be `Throwable`, given that the majority of proper effect types out there can only fail with `Throwable`-s. Except for ZIO, but users of ZIO are probably better off with using ZIO Schedules anyway. Another possibility, when this E may not be a `Throwable` is when one uses `EitherT` for example.

```scala
import com.emarsys.scheduler.Schedule
import com.emarsys.scheduler.syntax._

val effect: F[A]
val policy: Schedule[F, E, B]

// A MonadError[F, E] must be available implicitly

val retried: F[A] = effect retry policy
```

## Predefined schedules

Predefined schedules are available on the `Schedule` companion object.

Never executing schedule
```scala
Schedule.never: Schedule[F, Any, Nothing]
```

Recurring forever, without delay, returning how many times it has run so far.
```scala
Schedule.forever: Schedule[F, Any, Int]
```

Forever is implemented in terms of unfold
```scala
Schedule.unfold[B](zero: => B)(f: B => B): Schedule[F, Any, B]

Schedule.forever = Schedule.unfold(0)(_ + 1)
```

Like forever, but with an initial delay
```scala
Schedule.after(delay: FiniteDuration): Schedule[F, Any, Int]
```

Fixed number of occurences
```scala
Schedule.occurs(times: Int): Schedule[F, Any, Int]
```

Fixed spacing between occurences (not considering the time the effect takes to produce an output)
```scala
Schedule.spaced(delay: FiniteDuration): Schedule[F, Any, Int]
```

The identity schedule returns the latest output of the effect.
```scala
Schedule.identity[A]: Schedule[F, A, A]
```

Recur while a predicate holds
```scala
Schedule.whileInput[A](p: A => Boolean): Schedule[F, A, Int]
```

Recur until a predicate holds
```scala
Schedule.untilInput[A](p: A => Boolean): Schedule[F, A, Int]
```

Linearly increasing delays
```scala
Schedule.linear(unit: FiniteDuration): Schedule[F, Any, FiniteDuration]
```

Increase delays according to the fibonacci sequence
```scala
Schedule.fibonacci(one: FiniteDuration): Schedule[F, Any, FiniteDuration]
```

Exponentially increasing delays
```scala
Schedule.exponential(unit: FiniteDuration, base: Double = 2.0): Schedule[F, Any, FiniteDuration]
```

Time capped schedule
```scala
Schedule.maxFor(timeCap: FiniteDuration): Schedule[F, Any, FiniteDuration]
```
This will not cancel the effect, just won't continue after the specified time has passed.


## Combinators

Add an initial delay to a schedule, it does not change anything else
```scala
#after(delay: FiniteDuration): Schedule[F, A, B]
```

Reconsider the decision of a schedule. The boolean output of the function will be used to determine whether the schedule should continue.
```scala
#reconsider(f: Decision[State, B] => Boolean): Schedule[F, A, B]
```

Fold over the outputs of a schedule. Changes the output type of the schedule.
```scala
#fold[Z](z: Z)((Z, B) => Z): Schedule[F, A, Z]
```

Collect the outputs of a schedule (implemented via fold)
```scala
#collect: Schedule[F, A, List[B]]
```

The intersection of two schedules continue only if both schedules want to continue and uses the maximum of the delays.

```scala
(Schedule[F, A, B] && Schedule[F, A, C]): Schedule[F, A, (B, C)]
```

The union of two schedules is the opposite: it stops only when both of the schedules want to stop and uses the minimum of the delays

```scala
(Schedule[F, A, B] || Schedule[F, A, C]): Schedule[F, A, (B, C)]
```

The `<*` operator works like `&&` but it only keeps the output of the schedule on the left hand side.
```scala
(Schedule[F, A, B] <* Schedule[F, A, C]): Schedule[F, A, B]
```

The `*>` operator works like `&&` but it only keeps the output of the schedule on the right hand side.
```scala
(Schedule[F, A, B] *> Schedule[F, A, C]): Schedule[F, A, C]
```

A schedule can be executed after the other with the `andAfterThat` operator. If two schedules are combined this way, then first the schedule on the left hand side is used until it terminates, and from there the schedule on the right hand side will be used.
```scala
(Schedule[F, A, B] andAfterThat Schedule[F, A, C]): Schedule[F, A, Either[B, C]]
```

Schedules also have function-like composition.

```scala
(Schedule[F, A, B] >>> Schedule[F, B, C]): Schedule[F, A, C]
(Schedule[F, A, B] <<< Schedule[F, A0, A]): Schedule[F, A0, C]
```

## An example similar to the one mentioned in [John A. DeGoes's talk on ZIO Schedules]

Produce a schedule that starts with an exponential spacing from 10 millis and after it reaches 60 seconds it switches over to a fixed 60s delay, but it will do that only up to 100 times, emitting the list of collected outputs from the effect.

```scala
(Schedule.exponential(10 millis).reconsider(_.delay < 60 seconds)
  andAfterThat (Schedule.spaced(60 seconds) && Schedule.occurs(100)))
  *> Schedule.collect
```


[ZIO Schedule]: https://scalaz.github.io/scalaz-zio/datatypes/schedule.html
[John A. DeGoes's talk on ZIO Schedules]: https://www.youtube.com/watch?v=onQSHiafAY8
