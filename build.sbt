organization       := "com.emarsys"
name               := "scheduler"
crossScalaVersions := List("2.13.10", "2.12.18")

scalacOptions ++= scalacOptionsFor(scalaVersion.value)

scalafmtOnCompile := true

libraryDependencies += "org.typelevel"  %% "cats-core"            % "2.9.0"
libraryDependencies += "org.typelevel"  %% "cats-effect"          % "2.5.5"
libraryDependencies += "org.scalacheck" %% "scalacheck"           % "1.17.0" % Test
libraryDependencies += "org.scalatest"  %% "scalatest"            % "3.2.15" % Test
libraryDependencies += "org.typelevel"  %% "cats-laws"            % "2.9.0"  % Test
libraryDependencies += "org.typelevel"  %% "discipline-scalatest" % "2.2.0"  % Test
libraryDependencies += "org.typelevel"  %% "cats-effect-laws"     % "2.5.5"  % Test

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full)

licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT"))
homepage := Some(url("https://github.com/emartech/pure-scheduler"))
developers := List(
  Developer("doczir", "Robert Doczi", "doczi.r@gmail.com", url("https://github.com/doczir")),
  Developer("miklos-martin", "Miklos Martin", "miklos.martin@gmail.com", url("https://github.com/miklos-martin")),
  Developer("suliatis", "Attila Suli", "suli.zakar.attila@gmail.com", url("https://github.com/suliatis"))
)

def scalacOptionsFor(scalaVersion: String) =
  Seq(
    "-language:higherKinds",
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-explaintypes",
    "-unchecked",
    "-feature",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-Ywarn-dead-code",
    "-Ywarn-extra-implicit",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused:implicits",
    "-Ywarn-unused:imports",
    "-Ywarn-unused:locals",
    "-Ywarn-unused:params",
    "-Ywarn-unused:patvars",
    "-Ywarn-unused:privates",
    "-Xlint",
    "-P:kind-projector:underscore-placeholders"
  ) ++ (if (is2_12(scalaVersion))
          Seq(
            "-Ypartial-unification",
            "-Yno-adapted-args",
            "-Ywarn-inaccessible",
            "-Ywarn-nullary-override",
            "-Ywarn-nullary-unit",
            "-Ywarn-infer-any"
          )
        else Seq())

def is2_12(scalaVersion: String): Boolean =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, 12)) => true
    case _             => false
  }
