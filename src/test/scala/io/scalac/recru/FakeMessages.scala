package io.scalac.recru

import akka.Done
import io.scalac.recru.Model.{Color, GameId, Move, Player}

class FakeMessages extends Messages {
  override def listenLocation: String = ""

  var gamesStarted = Seq.empty[Set[Model.Player]]
  override def signalGameStart(players: Set[Player]): Done = {
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
}
