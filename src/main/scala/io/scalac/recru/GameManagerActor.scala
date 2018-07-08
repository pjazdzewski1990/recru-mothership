package io.scalac.recru

import java.util.concurrent.TimeUnit

import akka.Done
import akka.pattern.ask
import akka.actor.{Actor, ActorRef}
import akka.util.Timeout
import io.scalac.recru.GameActor.JoinResult
import io.scalac.recru.GameService.Player

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object GameManagerActor {
  sealed trait GameManagerCommand
  case class FindGameForPlayer(player: Player) extends GameManagerCommand

  case class GameStarted(players: Seq[Player]) extends GameManagerCommand

  sealed trait FindGameResult
  case object Found extends FindGameResult
}

class GameManagerActor() extends Actor {
  import GameManagerActor._

  implicit val timeout = Timeout(3, TimeUnit.SECONDS)
  implicit val ec: ExecutionContext = context.dispatcher

  def handleCommands(gamesRunning: Seq[ActorRef], gameWaiting: Option[ActorRef]): Receive = {
    case msg: FindGameForPlayer if gameWaiting.isDefined =>
      tryJoining(gameWaiting.get, msg)

    case msg: FindGameForPlayer if gameWaiting.isEmpty =>
      val newGame = context.actorOf(GameActor.props(self, new Messages {
        override def signalGameStart(players: Seq[Player]): Done = Done
      }, playersWaitTimeout = 30.seconds))
      tryJoining(gameWaiting.get, msg)

      context.become(handleCommands(gamesRunning, Option(newGame)))
  }

  //TODO: this signature is not quite right
  private def tryJoining(gameWaiting: ActorRef, request: FindGameForPlayer) = {
    val replyTo = sender()
    val retryWith = self

    (gameWaiting ? GameActor.JoinGame(request.player)).mapTo[JoinResult].foreach {
      case GameActor.Joined =>
        replyTo ! Found
      case _ =>
        // we will try again in a moment, but we need to overwrite the sender so reply will arrive correctly
        retryWith.tell(request, replyTo)
    }
  }

  override def receive: Receive = handleCommands(Nil, None)
}
