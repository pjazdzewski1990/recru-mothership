package io.scalac.recru

import scala.util.Random

// classes which utility goes beyond a single service
object Model {
  case class Player(name: String) extends AnyVal
  case class GameId(v: String) extends AnyVal

  sealed trait Move {
    def moveValue: Int
  }
  case object BackTwoFields extends Move {
    override val moveValue: Int = -2
  }
  case object BackOneField extends Move {
    override val moveValue: Int = -1
  }
  case object ForwardOneField extends Move {
    override val moveValue: Int = 1
  }
  case object ForwardTwoFields extends Move {
    override val moveValue: Int = 2
  }

  sealed trait Color
  case object Yellow extends Color
  case object Orange extends Color
  case object Red extends Color
  case object Blue extends Color
  case object Green extends Color
  case object Purple extends Color

  object Colors {
    def fromString(c: String): Color = c.toLowerCase match {
      case "yellow" => Yellow
      case "orange" => Orange
      case "red" => Red
      case "blue" => Blue
      case "green" => Green
      case "purple" => Purple
    }

    def randomColors(): Seq[Color] = Random.shuffle(Seq(Yellow, Orange, Red, Blue, Green, Purple))
  }
}
