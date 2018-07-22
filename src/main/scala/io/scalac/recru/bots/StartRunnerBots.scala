package io.scalac.recru.bots

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

//TODO: when they are confirmed to work fine, move to them out. To a separate project/lib etc.
object StartRunnerBots extends App {
  // this is an app that can be started aside to run some preconfigured bots

  implicit val system = ActorSystem("clients")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val client = new PlayHttpComms("http://localhost:8080/")
  system.actorOf(RunnerPlayer.props("bob", client))
  system.actorOf(RunnerPlayer.props("joe", client))
}
