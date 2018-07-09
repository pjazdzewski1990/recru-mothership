package io.scalac.recru

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import io.scalac.recru.Model._

import scala.concurrent.{ExecutionContext, Future}

object GameService {
  case class Game(id: GameId, listenOn: String)

  sealed trait MoveResult
  case object Moved extends MoveResult
  case object InvalidMove extends MoveResult
  case object WrongTurn extends MoveResult
}

trait GameService {
  import GameService._
  def searchForAGame(p: Player): Future[Game]
  def move(game: GameId, p: Player, move: Int): Future[MoveResult]
}

case class ActorGameService(gameManager: ActorRef)(implicit ec: ExecutionContext) extends GameService {
  import GameService._

  implicit val timeout = Timeout(5, TimeUnit.SECONDS)

  override def searchForAGame(p: Player): Future[GameService.Game] = {
    (gameManager ? GameManagerActor.FindGameForPlayer(p)).map {
      case GameManagerActor.Found(id, listenOn) =>
       Game(id, listenOn)
    }
  }

  private val allowedMoves = Seq(-2, -1, 1, 2)
  override def move(game: GameId, player: Player, move: Int): Future[MoveResult] = {
    if(allowedMoves.contains(move)) {
      (gameManager ? GameManagerActor.MakeAMove(game, player, move)).map {
        case GameManagerActor.Moved =>
          Moved
        case GameManagerActor.NotYourTurn =>
          WrongTurn
      }
    } else {
      Future.successful(InvalidMove)
    }
  }
}