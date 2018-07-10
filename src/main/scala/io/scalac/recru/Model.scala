package io.scalac.recru

// classes which utility goes beyond
object Model {
  case class Player(name: String) extends AnyVal
  case class GameId(v: String) extends AnyVal

  sealed trait Move
  case object BackTwoFields extends Move
  case object BackOneField extends Move
  case object ForwardOneField extends Move
  case object ForwardTwoFields extends Move

  sealed trait Color
  case object Yellow extends Color
  case object Orange extends Color
  case object Red extends Color
  case object Blue extends Color
  case object Green extends Color
  case object Purple extends Color
}
