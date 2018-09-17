package io.scalac.recru

import io.scalac.recru.Model.Player

import scala.util.Random

object Randomizer {
  def createOrderFromPlayers(s: Set[Player]): Stream[Player] = {
    def createOrderFromPlayersInternal(rand: Seq[Player]): Stream[Player] = Stream.concat(rand) #::: createOrderFromPlayersInternal(rand)
    createOrderFromPlayersInternal(Random.shuffle(s.toSeq))
  }
}
