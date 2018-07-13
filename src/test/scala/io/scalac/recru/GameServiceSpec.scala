package io.scalac.recru

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import io.scalac.recru.GameService.{GameJoined, Moved, WrongTurn}
import io.scalac.recru.Model._
import io.scalac.recru.messaging.Signals.SignalListenLocation
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, MustMatchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import scala.concurrent.ExecutionContext.Implicits.global

class GameServiceSpec extends TestKit(ActorSystem("GameActor"))
  with FlatSpecLike with MustMatchers with BeforeAndAfterAll with Eventually with ScalaFutures {

  val p1 = Player("p1")
  val listenLocation = SignalListenLocation("loc")

  "GameService" should "search and find a game" in {
    val gameId = GameId("black-jack")

    val probe = TestProbe()

    val service = new ActorGameService(probe.ref)
    val result = service.searchForAGame(p1)

    probe.expectMsg(GameManagerActor.FindGameForPlayer(p1))
    probe.reply(GameManagerActor.GameFound(gameId, listenLocation, Red))
    result.futureValue mustBe GameJoined(gameId, listenLocation, Red)
  }

  it should "pass down and react to valid moves" in {
    val gameId = GameId("foo-bar")

    val probe = TestProbe()

    val service = new ActorGameService(probe.ref)
    val result = service.move(gameId, p1, Blue, ForwardOneField)

    probe.expectMsg( GameManagerActor.MakeAMove(gameId, p1, Blue, ForwardOneField))
    probe.reply(GameManagerActor.Moved)
    result.futureValue mustBe Moved
  }

  it should "pass down  and act on invalid moves" in{
    val gameId = GameId("bar-baz")

    val probe = TestProbe()

    val service = new ActorGameService(probe.ref)
    val result = service.move(gameId, p1, Blue, ForwardOneField)

    probe.expectMsg( GameManagerActor.MakeAMove(gameId, p1, Blue, ForwardOneField))
    probe.reply(GameManagerActor.NotYourTurn)
    result.futureValue mustBe WrongTurn
  }
}
