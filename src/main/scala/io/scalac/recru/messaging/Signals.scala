package io.scalac.recru.messaging

import akka.Done
import io.scalac.recru.Model._

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