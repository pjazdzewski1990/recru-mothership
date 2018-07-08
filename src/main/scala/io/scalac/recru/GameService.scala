package io.scalac.recru

import java.util.UUID

import scala.concurrent.Future

object GameService {
  case class Player(name: String) extends AnyVal

  case class GameId(v: String) extends AnyVal
  case class Game(id: GameId, listenOn: String)
}

trait GameService {
  import GameService._
  def searchForAGame(p: Player): Future[Game]
}

case class ActorGameService() extends GameService {
  import GameService._
  override def searchForAGame(p: GameService.Player): Future[GameService.Game] = Future.successful(
    Game(
      GameId(UUID.randomUUID().toString),
      UUID.randomUUID().toString
    )
  )
}