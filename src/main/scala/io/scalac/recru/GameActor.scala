package io.scalac.recru

import akka.actor.{ActorRef, FSM, Props}
import io.scalac.recru.GameActorInternals.{GameData, State}
import io.scalac.recru.GameManagerActor.GameStarted
import io.scalac.recru.GameService.Player

import scala.concurrent.duration._

object GameActor {
  def props(manager: ActorRef,
            messages: Messages,
            playersWaitTimeout: FiniteDuration): Props =
    Props(classOf[GameActor], manager, messages, playersWaitTimeout)

  sealed trait GameActorCommand // marker
  case class JoinGame(p: Player) extends GameActorCommand

  sealed trait JoinResult
  case object Joined extends JoinResult
  case object GameIsAlreadyRunning extends JoinResult

  val maxPlayersInGame = 6
}

object GameActorInternals {

  sealed trait State // marker
  case object WaitingForPlayers extends State
  case object WaitingForCommand extends State
  case object Done extends State

  case class GameData(playersInTheGame: Seq[Player], boardState: Seq[Seq[Player]])

  def emptyField(size: Int = 10): Seq[Seq[Player]] = Seq.fill(size)(Seq.empty)
}

class GameActor(manager: ActorRef,
                messages: Messages,
                playersWaitTimeout: FiniteDuration) extends FSM[State, GameData] {
  import GameActor._
  import GameActorInternals._

  startWith(WaitingForPlayers, GameData(Seq.empty, Seq.empty))

  when(WaitingForPlayers, playersWaitTimeout) {
    case Event(JoinGame(p), data) if data.playersInTheGame.size < maxPlayersInGame =>
      val updated = data.copy(playersInTheGame = data.playersInTheGame :+ p)

      sender() ! Joined

      if(updated.playersInTheGame.size == maxPlayersInGame) {

        messages.signalGameStart(updated.playersInTheGame)

        goto(WaitingForCommand) using updated
      } else {
        stay() using updated
      }

    case Event(StateTimeout, data) if data.playersInTheGame.size > 1 =>
      messages.signalGameStart(data.playersInTheGame)
      goto(WaitingForCommand)
  }

  onTransition {
    case WaitingForPlayers -> WaitingForCommand =>
      manager ! GameStarted(stateData.playersInTheGame)
  }

  when(WaitingForCommand) {
    case Event(_, data) if data.playersInTheGame.size == 0 => stay()
  }

  whenUnhandled {
    case Event(JoinGame(_), _) =>
      sender() ! GameIsAlreadyRunning
      stay()
  }
}
