name := "aggregateTask"

version := "0.1"

scalaVersion := "2.13.6"

val AkkaVersion = "2.6.17"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test
)
libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.6"
libraryDependencies += "com.typesafe" % "config" % "1.4.1"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.10"
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.9.2"


