package io.scalac.recru

import akka.actor.{ActorRef, FSM, Props}
import io.scalac.recru.GameActorInternals.{GameData, State}
import io.scalac.recru.GameManagerActor.GameStarted
import io.scalac.recru.Model._

import scala.concurrent.duration._

object GameActor {
  def props(gameId: GameId,
            manager: ActorRef,
            messages: Messages,
            playersWaitTimeout: FiniteDuration): Props =
    Props(new GameActor(gameId, manager, messages, playersWaitTimeout))

  sealed trait GameActorCommand // marker
  case class JoinGame(player: Player) extends GameActorCommand
  case class PlayerMoves(player: Player, move: Move) extends GameActorCommand

  sealed trait JoinResult
  case object Joined extends JoinResult
  case object GameIsAlreadyRunning extends JoinResult

  sealed trait MoveResult
  case object NotYourTurn extends MoveResult //TODO: handle 0 moves?
  case object Moved extends MoveResult

  val maxPlayersInGame = 6
}

object GameActorInternals {

  sealed trait State // marker
  case object WaitingForPlayers extends State
  case object WaitingForCommand extends State
  case object Done extends State

  case class GameData(playersInTheGame: Set[Player], boardState: Seq[Seq[Player]], order: Option[Stream[Player]]) {
    def skipToNextPlayer(): GameData = {
      copy(order = order.map(_.drop(1)))
    }

    def updateBoard(): GameData = {
      this //TODO: implement movement
    }
  }

  def emptyField(size: Int = 10): Seq[Seq[Player]] = Seq.fill(size)(Seq.empty)

  def createOrder(s: Set[Player]): Stream[Player] = Stream.concat(s) #::: createOrder(s)
}

class GameActor(gameId: GameId,
                manager: ActorRef,
                messages: Messages,
                playersWaitTimeout: FiniteDuration) extends FSM[State, GameData] {
  import GameActor._
  import GameActorInternals._

  startWith(WaitingForPlayers, GameData(Set.empty, Seq.empty, None))

  when(WaitingForPlayers, playersWaitTimeout) {
    case Event(JoinGame(p), data) if data.playersInTheGame.size < maxPlayersInGame =>
      val updated = data.copy(playersInTheGame = data.playersInTheGame + p)

      sender() ! Joined

      if(updated.playersInTheGame.size == maxPlayersInGame) {
        messages.signalGameStart(updated.playersInTheGame)
        //TODO: make sure you can't move to WaitingForCommand without setting the order
        val orderOfPlayers = Option(createOrder(updated.playersInTheGame))
        goto(WaitingForCommand) using updated.copy(order = orderOfPlayers)
      } else {
        stay() using updated
      }

    case Event(StateTimeout, data) if data.playersInTheGame.size > 1 =>
      messages.signalGameStart(data.playersInTheGame)
      val orderOfPlayers = Option(createOrder(data.playersInTheGame))
      goto(WaitingForCommand) using data.copy(order = orderOfPlayers)
  }

  onTransition {
    case WaitingForPlayers -> WaitingForCommand =>
      log.info("Game {} started", gameId)
      manager ! GameStarted(stateData.playersInTheGame)
  }

  when(WaitingForCommand) {
    case Event(PlayerMoves(p, _), data) if data.order.map(_.head != p).getOrElse(true) =>
      sender() ! NotYourTurn
      stay()
    case Event(PlayerMoves(p, m), data) if data.order.map(_.head == p).getOrElse(false) =>
      messages.signalGameUpdate(gameId, p, m)
      sender() ! Moved
      stay() using data.skipToNextPlayer().updateBoard()
  }

  whenUnhandled {
    case Event(JoinGame(_), _) =>
      sender() ! GameIsAlreadyRunning
      stay()
  }
}
