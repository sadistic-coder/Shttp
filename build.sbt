name := "Shttp"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test"
libraryDependencies += "org.typelevel" %% "cats-core" % "1.6.0"
libraryDependencies += "org.typelevel" %% "cats-effect" % "1.2.0"

val http4sVersion = "0.19.0"

// Only necessary for SNAPSHOT releases
resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion,
  "org.http4s" %% "http4s-twirl" % http4sVersion
)
scalacOptions ++= Seq("-Ypartial-unification")
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.3" % Runtime

libraryDependencies ++= Seq(
  // Start with this one
  "org.tpolecat" %% "doobie-core"      % "0.6.0",
  // And add any of these as needed
  "org.tpolecat" %% "doobie-h2"        % "0.6.0",          // H2 driver 1.4.197 + type mappings.
  "org.tpolecat" %% "doobie-hikari"    % "0.6.0",          // HikariCP transactor.
  "org.tpolecat" %% "doobie-postgres"  % "0.6.0",          // Postgres driver 42.2.5 + type mappings.
  "org.tpolecat" %% "doobie-specs2"    % "0.6.0" % "test", // Specs2 support for typechecking statements.
  "org.tpolecat" %% "doobie-scalatest" % "0.6.0" % "test"  // ScalaTest support for typechecking statements.
)

libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.12"
scalacOptions := Seq("-deprecation", "-encoding", "utf8")

enablePlugins(SbtTwirl)