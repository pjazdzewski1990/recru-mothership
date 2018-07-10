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
  case class PlayerMoves(player: Player, whichColorToMove: Color, move: Move) extends GameActorCommand

  sealed trait JoinResult
  case class Joined(colorAssigned: Color) extends JoinResult
  case object GameIsAlreadyRunning extends JoinResult

  sealed trait MoveResult
  case object NotYourTurn extends MoveResult
  case object Moved extends MoveResult

  val maxPlayersInGame = 6
}

object GameActorInternals {

  sealed trait State // marker
  case object WaitingForPlayers extends State
  case object WaitingForCommand extends State
  case object Done extends State

  case class GameData(playersInTheGame: Map[Color, Player], boardState: List[Seq[Color]], order: Stream[Player]) {
    def skipToNextPlayer(): GameData = {
      copy(order = order.drop(1))
    }

    def updateBoard(color: Color, move: Move): GameData = {
      // TODO: should never fail if board is constructed right, but more safety would be appreciated
      val (fieldContainingColor, fieldContainingColorIdx) = boardState.zipWithIndex.find(_._1.contains(color)).get
      val fieldToPutTheColorIdx = Math.max(0, Math.min(boardState.size - 1, fieldContainingColorIdx + move.moveValue))

      val (notMovingThisTurn, movingThisTurn) = if(fieldContainingColorIdx != 0 && fieldContainingColorIdx != boardState.size - 1) {
        fieldContainingColor.splitAt(fieldContainingColor.indexOf(color))
      } else {
        (fieldContainingColor.filter(_ != color), fieldContainingColor.filter(_ == color))
      }
      val boardWithColorRemoved: List[Seq[Color]] = boardState.patch(
        from = fieldContainingColorIdx,
        patch = Seq(notMovingThisTurn),
        replaced = 1)

      val oldValuesAtUpdateIdx = boardWithColorRemoved(fieldToPutTheColorIdx)
      val boardWithColorAddedAgain = boardWithColorRemoved.patch(
        from = fieldToPutTheColorIdx,
        patch = Seq(oldValuesAtUpdateIdx ++ movingThisTurn),
        replaced = 1)

      copy(boardState = boardWithColorAddedAgain)
    }
  }

  object GameData {
    def empty(): GameData = {
      val initialBoardState = Seq(Yellow, Orange, Red, Blue, Green, Purple) :: List.fill(9)(Seq.empty)
      GameData(Map.empty, initialBoardState, createOrderFromPlayers(Set(Player(""))))
    }
  }

  def createOrderFromPlayers(s: Set[Player]): Stream[Player] = Stream.concat(s) #::: createOrderFromPlayers(s)
}

class GameActor(gameId: GameId,
                manager: ActorRef,
                messages: Messages,
                playersWaitTimeout: FiniteDuration) extends FSM[State, GameData] {
  import GameActor._
  import GameActorInternals._

  startWith(WaitingForPlayers, GameData.empty())

  when(WaitingForPlayers, playersWaitTimeout) {
    case Event(JoinGame(p), data) if data.playersInTheGame.size < maxPlayersInGame =>

      val color = Colors.randomColors().find(!data.playersInTheGame.contains(_)).get //TODO: this .get should never fail, but more safety would be appreciated
      val updated = data.copy(playersInTheGame = data.playersInTheGame + (color -> p))
      sender() ! Joined(color)

      if(updated.playersInTheGame.size == maxPlayersInGame) {
        val players = updated.playersInTheGame.values.toSet
        messages.signalGameStart(players)
        //TODO: make sure you can't move to WaitingForCommand without setting the order
        val orderOfPlayers = createOrderFromPlayers(players)
        goto(WaitingForCommand) using updated.copy(order = orderOfPlayers)
      } else {
        stay() using updated
      }

    case Event(StateTimeout, data) if data.playersInTheGame.size > 1 =>
      val players = data.playersInTheGame.values.toSet
      messages.signalGameStart(players)
      val orderOfPlayers = createOrderFromPlayers(players)
      goto(WaitingForCommand) using data.copy(order = orderOfPlayers)
  }

  onTransition {
    case WaitingForPlayers -> WaitingForCommand =>
      log.info("Game {} started", gameId)
      manager ! GameStarted(stateData.playersInTheGame.values.toSet)
  }

  when(WaitingForCommand) {
    case Event(PlayerMoves(p, _, _), data) if data.order.head != p =>
      sender() ! NotYourTurn
      stay()
    case Event(PlayerMoves(p, c, m), data) if data.order.head == p =>
      messages.signalGameUpdate(gameId, p, c, m)
      sender() ! Moved
      stay() using data.skipToNextPlayer().updateBoard(c, m)
  }

  whenUnhandled {
    case Event(JoinGame(_), _) =>
      sender() ! GameIsAlreadyRunning
      stay()
  }
}
