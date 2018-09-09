organization := "com.emarsys"
name := "pure-scheduler"
scalaVersion := "2.12.6"

scalacOptions ++= Seq(
  "-language:higherKinds",
  "-deprecation",
  "-Ypartial-unification",
  "-encoding",
  "UTF-8",
  "-explaintypes",
  "-unchecked",
  "-feature",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-Ywarn-dead-code",
  "-Ywarn-extra-implicit",
  "-Ywarn-inaccessible",
  "-Ywarn-infer-any",
  "-Ywarn-nullary-override",
  "-Ywarn-nullary-unit",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused:implicits",
  "-Ywarn-unused:imports",
  "-Ywarn-unused:locals",
  "-Ywarn-unused:params",
  "-Ywarn-unused:patvars",
  "-Ywarn-unused:privates",
  "-Yno-adapted-args",
  "-Xlint"
)

libraryDependencies += "org.scalatest" % "scalatest_2.12" % "3.0.1" % "test"
libraryDependencies += "org.typelevel" %% "cats-core"     % "1.3.1"
libraryDependencies += "org.typelevel" %% "cats-free"     % "1.3.1"
libraryDependencies += "org.typelevel" %% "cats-effect"   % "1.0.0"

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7")
addCompilerPlugin("io.tryp"        % "splain"          % "0.3.1" cross CrossVersion.patch)
