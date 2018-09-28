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

scalafmtOnCompile := true

libraryDependencies += "org.scalatest" % "scalatest_2.12"    % "3.0.1" % "test"
libraryDependencies += "org.typelevel" %% "cats-core"        % "1.3.1"
libraryDependencies += "org.typelevel" %% "cats-free"        % "1.3.1"
libraryDependencies += "org.typelevel" %% "cats-effect"      % "1.0.0"
libraryDependencies += "org.typelevel" %% "cats-effect-laws" % "1.0.0" % "test"

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7")
addCompilerPlugin("io.tryp"        % "splain"          % "0.3.1" cross CrossVersion.patch)

inThisBuild(
  List(
    licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
    homepage := Some(url("https://github.com/emartech/pure-scheduler")),
    developers := List(
      Developer("doczir", "Robert Doczi", "doczi.r@gmail.com", url("https://github.com/doczir")),
      Developer("miklos-martin", "Miklos Martin", "miklos.martin@gmail.com", url("https://github.com/miklos-martin"))
    ),
    scmInfo := Some(
      ScmInfo(url("https://github.com/emartech/pure-scheduler"), "scm:git:git@github.com:emartech/pure-scheduler.git")
    ),
    // These are the sbt-release-early settings to configure
    pgpPublicRing := file("./ci/local.pubring.asc"),
    pgpSecretRing := file("./ci/local.secring.asc"),
    releaseEarlyWith := SonatypePublisher
  )
)
