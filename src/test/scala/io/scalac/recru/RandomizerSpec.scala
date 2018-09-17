package io.scalac.recru

import io.scalac.recru.Model.Player
import org.scalatest.{FlatSpec, MustMatchers}

class RandomizerSpec extends FlatSpec with MustMatchers {
  it should "provide order of players that is random and long enough" in {
    val order1 = Randomizer.createOrderFromPlayers(
      Set("player1", "player2", "player3", "player99", "bob", "eve").map(Player)
    )
    val order2 = Randomizer.createOrderFromPlayers(
      Set("player1", "player2", "player3", "player99", "bob", "eve").map(Player)
    )

    val suborder1 = order1.take(100)
    val suborder2 = order2.take(100)

    suborder1.size mustBe 100
    suborder2.size mustBe 100
    suborder1 mustNot be(suborder2)
  }
}
