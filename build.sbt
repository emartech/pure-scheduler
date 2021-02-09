organization := "com.emarsys"
name := "scheduler"
crossScalaVersions := List("2.13.0", "2.12.10")

scalacOptions ++= scalacOptionsFor(scalaVersion.value)

scalafmtOnCompile := true

libraryDependencies += "org.typelevel"  %% "cats-core"            % "2.4.0"
libraryDependencies += "org.typelevel"  %% "cats-effect"          % "2.2.0"
libraryDependencies += "org.scalacheck" %% "scalacheck"           % "1.14.3" % Test
libraryDependencies += "org.scalatest"  %% "scalatest"            % "3.2.2" % Test
libraryDependencies += "org.typelevel"  %% "cats-laws"            % "2.4.0" % Test
libraryDependencies += "org.typelevel"  %% "discipline-scalatest" % "2.0.1" % Test
libraryDependencies += "org.typelevel"  %% "cats-effect-laws"     % "2.2.0" % Test

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")
addCompilerPlugin("io.tryp"       % "splain"          % "0.4.1" cross CrossVersion.patch)

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
    "-Xlint"
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
