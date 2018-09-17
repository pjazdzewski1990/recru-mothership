package io.scalac.recru

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers
import akka.http.scaladsl.server.Directives._
import io.scalac.recru.GameService.GameJoined
import io.scalac.recru.Model._
import io.scalac.recru.Protocol._
import spray.json.{JsObject, JsString, JsValue}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class Routes(game: GameService) {
  // set of functions to translate internal representation to protocol entities
  // TODO: cut out the middle man
  def translateIncoming(p: IncomingPlayer): Player = Player(p.name)
  def translateIncoming(move: Int): Option[Move] = move match {
    case 2 => Option(ForwardTwoFields)
    case 1 => Option(ForwardOneField)
    case -1 => Option(BackOneField)
    case -2 => Option(BackTwoFields)
    case _ => None
  }
  def translateOutgoing(g: GameJoined): JsValue = JsObject(("gameId", JsString(g.id.v)), ("secretColor", JsString(g.colorAssigned.toString)))
  def translateOutgoing(res: GameService.MoveResult): Option[JsValue] = res match {
    case GameService.WrongTurn =>
      Option(JsObject(("errorCode", JsString("TURN"))))
    case GameService.InvalidMove =>
      Option(JsObject(("errorCode", JsString("MOVE"))))
    case _ =>
      None
  }

  private[Routes] val addRoute = entity(as[IncomingPlayer]) { p =>
    val joinGameF = game.searchForAGame(translateIncoming(p))
    onComplete(joinGameF) {
      case Success(gamePlayerJoined) =>
        respondWithHeader(headers.RawHeader("X-listen-on", gamePlayerJoined.listenOn.v)) {
          complete(OK, translateOutgoing(gamePlayerJoined))
        }
      case Failure(ex) =>
        complete((InternalServerError, s"An error occurred in the mothership: ${ex.getMessage}"))
    }
  }

  private[Routes] val moveRoute = path(Segment) { gameId =>
    entity(as[IncomingMove]) { move =>
      onComplete(movePiece(gameId, move)) {
        case Success(res) =>
          translateOutgoing(res) match {
            case Some(err) =>
              complete(BadRequest, err)
            case _ =>
              complete(OK)
          }
        case Failure(ex) =>
          complete((InternalServerError, s"An error occurred in the mothership: ${ex.getMessage}"))
      }
    }
  }

  def movePiece(gameId: String, move: IncomingMove): Future[GameService.MoveResult] = {
    val makeAMoveF = translateIncoming(move.move).map(
      game.move(GameId(gameId), Player(move.name), Colors.fromString(move.color), _)
    ).getOrElse {
      Future.successful(GameService.InvalidMove)
    }
    makeAMoveF
  }

  val router =
    ignoreTrailingSlash {
      pathPrefix("game") {
        pathEndOrSingleSlash { post { addRoute } } ~ put { moveRoute }
      }
    }
}
