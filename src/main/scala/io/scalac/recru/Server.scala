package io.scalac.recru

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import io.scalac.recru.messaging.{KafkaSignals, KafkaSink}


object Server {
  def main(args: Array[String]): Unit = {
    println("Starting the mothership!")

    implicit val system = ActorSystem("mothership")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    // dependencies
    val sink = new KafkaSink
    val gameManager = system.actorOf(GameManagerActor.props(new KafkaSignals(sink)))
    val gameService = new ActorGameService(gameManager)

    Http().bindAndHandle(new Routes(gameService).router, "0.0.0.0", port = 8080)

    println(s"Server online at http://localhost:8080/")
  }
}
