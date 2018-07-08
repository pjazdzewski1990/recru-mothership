package io.scalac.recru

import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import io.scalac.recru.GameActor.{GameIsAlreadyRunning, JoinGame, Joined}
import io.scalac.recru.GameManagerActor.GameStarted
import io.scalac.recru.GameService.Player
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfter, FlatSpecLike, MustMatchers}

import scala.concurrent.duration._

class GameActorSpec extends TestKit(ActorSystem("GameActor"))
  with FlatSpecLike with MustMatchers with BeforeAndAfter with Eventually with ScalaFutures {

  implicit val timeout = Timeout(3, TimeUnit.SECONDS)
  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  val player1 = Player("p1")
  val player2 = Player("p2")
  val player3 = Player("p3")

  class FakeMessages extends Messages {
    var seenPlayers: Seq[Player] = Nil
    override def signalGameStart(players: Seq[Player]): Done = {
      seenPlayers = seenPlayers ++ players
      Done
    }
    override def listenLocation: String = ""
  }

  "GameActor" should "start a game with 2 players after the timeout" in {
    val messages = new FakeMessages
    val manager = TestProbe()
    val game = system.actorOf(GameActor.props(manager.ref, messages, playersWaitTimeout = 1.second))
    val joined1F = game ? JoinGame(player1)
    val joined2F = game ? JoinGame(player2)

    joined1F.futureValue mustBe a[Joined.type]
    joined2F.futureValue mustBe a[Joined.type]

    eventually {
      messages.seenPlayers mustBe Seq(player1, player2)
    }
    manager.expectMsg(GameStarted(Seq(player1, player2)))
  }

  it should "immediately start the game when having {MAX} players" in {
    val messages = new FakeMessages
    val manager = TestProbe()
    val game = system.actorOf(GameActor.props(manager.ref, messages, playersWaitTimeout = 99.hours))

    (1 to GameActor.maxPlayersInGame).map(x => Player(x.toString)).map{ p =>
      (game ? JoinGame(p)).futureValue mustBe a[Joined.type]
    }

    eventually {
      messages.seenPlayers.length mustBe GameActor.maxPlayersInGame
    }
    manager.expectMsgClass(GameStarted(Seq.empty).getClass)
  }

  it should "reject players over the {MAX} players limit" in {
    val messages = new FakeMessages
    val manager = TestProbe()
    val game = system.actorOf(GameActor.props(manager.ref, messages, playersWaitTimeout = 99.hours))

    val players = (1 to GameActor.maxPlayersInGame * 2).map(x => Player(x.toString))

    players.take(GameActor.maxPlayersInGame).map{ p =>
      (game ? JoinGame(p)).futureValue mustBe a[Joined.type]
    }

    players.drop(GameActor.maxPlayersInGame).map{ p =>
      (game ? JoinGame(p)).futureValue mustBe a[GameIsAlreadyRunning.type]
    }
  }

  it should "end the game with a draw if nobody shows up" in {
    1 mustBe 2
  }

  it should "reject players who want to join a running game" in {
    val messages = new FakeMessages
    val manager = TestProbe()
    val game = system.actorOf(GameActor.props(manager.ref, messages, playersWaitTimeout = 1.second))
    val joined1F = game ? JoinGame(player1)
    val joined2F = game ? JoinGame(player2)

    joined1F.futureValue mustBe a[Joined.type]
    joined2F.futureValue mustBe a[Joined.type]

    eventually {
      messages.seenPlayers mustBe Seq(player1, player2)
    }
    manager.expectMsg(GameStarted(Seq(player1, player2)))

    (game ? JoinGame(player2)).futureValue mustBe a[GameIsAlreadyRunning.type]
  }

  it should "allow players to submit moves according to the defined order" in {
    1 mustBe 2
  }

  it should "reject player moves if provided out of order" in {
    1 mustBe 2
  }
}
