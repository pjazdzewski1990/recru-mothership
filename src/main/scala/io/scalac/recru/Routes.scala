package io.scalac.recru

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers
import akka.http.scaladsl.server.Directives._
import io.scalac.recru.GameService.{Game, Player}
import io.scalac.recru.Protocol._
import spray.json.{JsObject, JsString, JsValue}

import scala.util.{Failure, Success}

class Routes(game: GameService) {
  // set of functions to translate internal representation to protocol entities
  // TODO: cut out the middle man
  def translateIncoming(p: IncomingPlayer): Player = Player("p.name")
  def translateOutgoing(g: Game): JsValue = JsObject(("gameId", JsString(g.id.v)))

  val router = post {
    ignoreTrailingSlash {
      path("game") {
        entity(as[IncomingPlayer]) { p =>
          val joinGameF = game.searchForAGame(translateIncoming(p))
          onComplete(joinGameF) {
            case Success(gamePlayerJoined) =>
              respondWithHeader(headers.RawHeader("X-listen-on", gamePlayerJoined.listenOn)) {
                complete(OK, translateOutgoing(gamePlayerJoined))
              }
            case Failure(ex) =>
              complete((InternalServerError, s"An error occurred in the mothership: ${ex.getMessage}"))
          }
        }
      }
    }
  }
}
