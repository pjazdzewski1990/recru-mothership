package io.scalac.recru

import akka.Done
import io.scalac.recru.GameService.Player

trait Messages {
  def signalGameStart(players: Seq[Player]): Done
}
