import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.6",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "mothership",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.5.13",
      "com.typesafe.akka" %% "akka-http"   % "10.1.3",
      "com.typesafe.akka" %% "akka-stream" % "2.5.13",
      "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.3",
      "com.typesafe.akka" %% "akka-testkit" % "2.5.13" % Test,
      scalaTest % Test
    )
  )
