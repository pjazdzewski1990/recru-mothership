package io.scalac.recru

import akka.Done
import io.scalac.recru.GameService.Player

trait Messages {
  def listenLocation: String //TODO: stronger type-safety
  def signalGameStart(players: Seq[Player]): Done
}

class KafkaMessages extends Messages {
  override val listenLocation = "foo-topic"
  override def signalGameStart(players: Seq[Player]): Done = Done
}