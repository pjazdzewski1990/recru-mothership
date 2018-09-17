import Dependencies._

val akkaV = "2.5.13"
val akkaHttpV = "10.1.3"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "io.scalac",
      scalaVersion := "2.12.6",
      version      := "0.2.0-SNAPSHOT"
    )),
    name := "mothership",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-stream" % akkaV,
      "com.typesafe.akka" %% "akka-http"   % akkaHttpV,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV,
      "com.typesafe.akka" %% "akka-stream-kafka" % "0.22",
      "com.typesafe"      %  "config" % "1.3.2",
      scalaTest           %  Test,
      "com.typesafe.akka" %% "akka-testkit" % akkaV % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % Test,
      "com.typesafe.play" %% "play-ahc-ws-standalone" % "2.0.0-M2"
    )
  )

//docker config
mainClass in Compile := Some("io.scalac.recru.Server")

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)
enablePlugins(AshScriptPlugin)

dockerBaseImage := "openjdk:jre-alpine"
