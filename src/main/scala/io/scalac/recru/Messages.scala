package io.scalac.recru

import akka.Done
import io.scalac.recru.Model._

trait Messages {
  def listenLocation: String //TODO: stronger type-safety
  def signalGameStart(players: Set[Player]): Done
  def signalGameUpdate(gameId: GameId, player: Player, move: Move): Done
}

class KafkaMessages extends Messages {
  override val listenLocation = "foo-topic"
  override def signalGameStart(players: Set[Player]): Done = {
    println(s"Started a game with ${players}")
    Done
  }
  // Note: it would be a more useful design to include the whole state of board in the message, but it's intentionally made harder
  override def signalGameUpdate(gameId: GameId, player: Player, move: Move): Done = {
    println(s"Updated game ${gameId} with ${player}, ${move}")
    Done
  }
}