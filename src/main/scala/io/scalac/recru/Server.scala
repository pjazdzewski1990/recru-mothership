package io.scalac.recru

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.io.StdIn

object Server {
  def main(args: Array[String]): Unit = {
    println("Starting the mothership!")

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    // dependencies
    val gameManager = system.actorOf(GameManagerActor.props(new KafkaMessages))
    val gameService = new ActorGameService(gameManager)

    val bindingFuture = Http().bindAndHandle(new Routes(gameService).router, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}
