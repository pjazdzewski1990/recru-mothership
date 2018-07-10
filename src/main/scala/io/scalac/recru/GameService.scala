package io.scalac.recru

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import io.scalac.recru.Model._

import scala.concurrent.{ExecutionContext, Future}

object GameService {
  case class GameJoined(id: GameId, listenOn: String, colorAssigned: Color)

  sealed trait MoveResult
  case object Moved extends MoveResult
  case object InvalidMove extends MoveResult
  case object WrongTurn extends MoveResult
}

trait GameService {
  import GameService._
  def searchForAGame(p: Player): Future[GameJoined]
  def move(game: GameId, p: Player, color: Color, move: Move): Future[MoveResult]
}

case class ActorGameService(gameManager: ActorRef)(implicit ec: ExecutionContext) extends GameService {
  import GameService._

  implicit val timeout = Timeout(5, TimeUnit.SECONDS)

  override def searchForAGame(p: Player): Future[GameService.GameJoined] = {
    (gameManager ? GameManagerActor.FindGameForPlayer(p)).map {
      case GameManagerActor.GameFound(id, listenOn, color) =>
       GameJoined(id, listenOn, color)
    }
  }

  override def move(game: GameId, player: Player, color: Color, move: Move): Future[MoveResult] = {
    (gameManager ? GameManagerActor.MakeAMove(game, player, color, move)).map {
      case GameManagerActor.Moved =>
        Moved
      case GameManagerActor.NotYourTurn =>
        WrongTurn
    }
  }
}