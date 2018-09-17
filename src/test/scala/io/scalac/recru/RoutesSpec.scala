package io.scalac.recru

import akka.http.scaladsl.model.StatusCodes
import io.scalac.recru.Model.{Color, GameId, Move, Red}
import org.scalatest.{FlatSpecLike, MustMatchers}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.scalac.recru.Protocol._
import io.scalac.recru.Protocol.IncomingPlayer
import io.scalac.recru.messaging.Signals.SignalListenLocation
import spray.json.{JsObject, JsString}

import scala.concurrent.Future

class RoutesSpec extends FlatSpecLike with MustMatchers with ScalatestRouteTest {

  import Model._

  class AccumulatingGameService extends GameService {
    var searches = Seq.empty[Player]
    override def searchForAGame(p: Player): Future[GameService.GameJoined] = {
      searches = searches :+ p
      Future.successful(
        GameService.GameJoined(GameId("game"), SignalListenLocation("kafka-topic"), Red)
      )
    }

    var moves = Seq.empty[(GameId, Player, Color, Move)]
    override def move(game: GameId, p: Player, color: Color, move: Move): Future[GameService.MoveResult] = {
      moves = moves :+ (game, p, color, move)
      Future.successful(
        GameService.Moved
      )
    }
  }

  "Routes" should "allow creating games" in {
    val fakeGame = new AccumulatingGameService
    val r = new Routes(fakeGame).router
    Post("/game", IncomingPlayer("player1")) ~> r ~> check{
      status mustBe StatusCodes.OK
      responseAs[JsObject].fields("gameId") mustBe JsString("game")
      fakeGame.searches mustBe Seq(Player("player1"))
    }
  }

  it should "allow creating games (trailing slash)" in {
    val fakeGame = new AccumulatingGameService
    val r = new Routes(fakeGame).router
    Post("/game/", IncomingPlayer("player99")) ~> r ~> check{
      status mustBe StatusCodes.OK
      responseAs[JsObject].fields("gameId") mustBe JsString("game")
      fakeGame.searches mustBe Seq(Player("player99"))
    }
  }

  it should "allow making moves" in {
    val fakeGame = new AccumulatingGameService
    val r = new Routes(fakeGame).router
    Put("/game/e3-42bb-46a6-a286-8c779fa1f7b9", IncomingMove("player1", "red", 2)) ~> r ~> check{
      status mustBe StatusCodes.OK
      fakeGame.moves mustBe Seq(
        (GameId("e3-42bb-46a6-a286-8c779fa1f7b9"), Player("player1"), Red, ForwardTwoFields)
      )
    }
  }

  it should "allow making moves (traling slash)" in {
    val fakeGame = new AccumulatingGameService
    val r = new Routes(fakeGame).router
    Put("/game/e3-42bb-46a6-a286-8c779fa1f111/", IncomingMove("player99", "blue", -1)) ~> r ~> check{
      status mustBe StatusCodes.OK
      fakeGame.moves mustBe Seq(
        (GameId("e3-42bb-46a6-a286-8c779fa1f111"), Player("player99"), Blue, BackOneField)
      )
    }
  }

  it should "reject invalid moves" in {
    val rejectingGame = new GameService {
      override def searchForAGame(p: Player): Future[GameService.GameJoined] = ???
      override def move(game: Model.GameId, p: Player, color: Color, move: Move): Future[GameService.MoveResult] = Future.successful(
        GameService.InvalidMove
      )
    }
    val r = new Routes(rejectingGame).router
    Put("/game/e3-42bb-46a6-a286-8c779fa1f7b9", IncomingMove("player1", "red", 9999)) ~> r ~> check{
      status mustBe StatusCodes.BadRequest
      responseAs[JsObject].fields("errorCode") mustBe JsString("MOVE")
    }
  }

  it should "reject moves out of order" in {
    val rejectingGame = new GameService {
      override def searchForAGame(p: Model.Player): Future[GameService.GameJoined] = ???
      override def move(game: Model.GameId, p: Model.Player, color: Color, move: Move): Future[GameService.MoveResult] = Future.successful(
        GameService.WrongTurn
      )
    }
    val r = new Routes(rejectingGame).router
    Put("/game/e3-42bb-46a6-a286-8c779fa1f7b9", IncomingMove("player1", "red", 2)) ~> r ~> check{
      status mustBe StatusCodes.BadRequest
      responseAs[JsObject].fields("errorCode") mustBe JsString("TURN")
    }
  }
}
