package io.scalac.recru

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

object GameService {
  //TODO: move to utils as this reaches beyond us this protocol
  case class Player(name: String) extends AnyVal
  case class GameId(v: String) extends AnyVal

  case class Game(id: GameId, listenOn: String)
}

trait GameService {
  import GameService._
  def searchForAGame(p: Player): Future[Game]
}

case class ActorGameService(gameManager: ActorRef)(implicit ec: ExecutionContext) extends GameService {
  import GameService._

  implicit val timeout = Timeout(5, TimeUnit.SECONDS)

  override def searchForAGame(p: GameService.Player): Future[GameService.Game] = {
    (gameManager ? GameManagerActor.FindGameForPlayer(p)).map {
      case GameManagerActor.Found(id, listenOn) =>
       Game(id, listenOn)
    }
  }
}