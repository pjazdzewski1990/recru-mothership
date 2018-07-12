package io.scalac.recru

import akka.Done
import io.scalac.recru.Model.{Color, GameId, Move, Player}
import io.scalac.recru.Signals.SignalListenLocation

class FakeSignals extends Signals {
  override def listenLocation = SignalListenLocation("")

  var gamesStarted = Seq.empty[Set[Model.Player]]
  override def signalGameStart(gameId: GameId, players: Set[Player]): Done = {
    gamesStarted = gamesStarted :+ players
    Done
  }

  var seenUpdates = Seq.empty[(GameId, Player, Color, Move)]
  override def signalGameUpdate(gameId: GameId, player: Player, color: Color, move: Move): Done = {
    seenUpdates = seenUpdates :+ (gameId, player, color, move)
    Done
  }

  var seenGameEnd: Option[(GameId, Seq[Player], Seq[Player])] =  None
  override def signalGameEnd(gameId: GameId, winners: Seq[Player], losers: Seq[Player]): Done = {
    seenGameEnd = Option((gameId, winners, losers))
    Done
  }

  var turns = Seq.empty[Player]
  override def signalTurn(gameId: GameId, playerMovingNow: Player): Done = {
    turns = turns :+ playerMovingNow
    Done
  }
}
