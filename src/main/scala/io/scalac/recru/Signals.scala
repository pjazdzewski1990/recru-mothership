package io.scalac.recru

import akka.Done
import io.scalac.recru.Model._
import io.scalac.recru.Signals.SignalListenLocation

object Signals {
  case class SignalListenLocation(v: String) extends AnyVal
}

trait Signals {
  import Signals._

  def listenLocation: SignalListenLocation
  def signalGameStart(gameId: GameId, players: Set[Player]): Done
  def signalTurn(gameId: GameId, playerMovingNow: Player): Done
  def signalGameUpdate(gameId: GameId, player: Player, movedColor: Color, move: Move): Done
  def signalGameEnd(gameId: GameId, winners: Seq[Player], losers: Seq[Player]): Done
}

class KafkaSignals extends Signals {
  override val listenLocation = SignalListenLocation("foo-topic")
  override def signalGameStart(gameId: GameId, players: Set[Player]): Done = {
    println(s"Started a game ${gameId} with ${players}")
    Done
  }
  override def signalTurn(gameId: GameId, playerMovingNow: Player): Done = {
    println(s"Game ${gameId} now waits on move from ${playerMovingNow}")
    Done
  }
  // Note: it would be a more useful design to include the whole state of board in the message, but it's intentionally made harder
  override def signalGameUpdate(gameId: GameId, player: Player, movedColor: Color, move: Move): Done = {
    println(s"Updated game ${gameId} with ${player}, ${move} for ${movedColor}")
    Done
  }

  override def signalGameEnd(gameId: GameId, winners: Seq[Player], losers: Seq[Player]): Done = {
    println(s"Game ${gameId} ended ${winners} losers ${losers}")
    Done
  }
}