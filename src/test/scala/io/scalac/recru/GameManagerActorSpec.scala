package io.scalac.recru

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import io.scalac.recru.GameManagerActor._
import io.scalac.recru.Model._
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, MustMatchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration._

class GameManagerActorSpec extends TestKit(ActorSystem("GameActor"))
  with FlatSpecLike with MustMatchers with BeforeAndAfterAll with Eventually with ScalaFutures {

  implicit val timeout = Timeout(3, TimeUnit.SECONDS)
  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val player1 = Player("p1")
  val player2 = Player("p2")
  val player3 = Player("p3")
  val player4 = Player("p4")

  "GameManagerActor" should "create a new game then moves games from waiting to running" in {
    val msg = new FakeSignals()
    val manager = system.actorOf(Props(new GameManagerActor(msg, playersWaitTimeout = 1.second, playersMoveTimeout = 1.minute)))
    (manager ? FindGameForPlayer(player1)).futureValue mustBe a[GameFound]
    (manager ? FindGameForPlayer(player2)).futureValue mustBe a[GameFound]
    (manager ? FindGameForPlayer(player3)).futureValue mustBe a[GameFound]
    eventually {
      msg.gamesStarted.length mustBe 1
      msg.gamesStarted.head mustBe Set(player1, player2, player3)
    }
  }

  it should "create new games when needed" in{
    val msg = new FakeSignals()
    val manager = system.actorOf(Props(new GameManagerActor(msg, playersWaitTimeout = 1.second, playersMoveTimeout = 1.minute)))

    (manager ? FindGameForPlayer(player1)).futureValue mustBe a[GameFound]
    (manager ? FindGameForPlayer(player2)).futureValue mustBe a[GameFound]

    eventually {
      msg.gamesStarted.length mustBe 1
      msg.gamesStarted.head mustBe Set(player1, player2)
    }

    (manager ? FindGameForPlayer(player3)).futureValue mustBe a[GameFound]
    (manager ? FindGameForPlayer(player4)).futureValue mustBe a[GameFound]

    eventually {
      msg.gamesStarted.length mustBe 2
      msg.gamesStarted.last mustBe Set(player3, player4)
    }
  }

  it should "rejects moves to games that still accept players, but passes them when game does start" in {
    def defaultOrder(s: Set[Player]): Stream[Player] =
      Stream.concat(s) #::: defaultOrder(s)

    val msg = new FakeSignals()
    val manager = system.actorOf(Props(new GameManagerActor(msg, playersWaitTimeout = 1.second, playersMoveTimeout = 1.minute, defaultOrder _)))

    val found = (manager ? FindGameForPlayer(player1)).futureValue
    found mustBe a[GameFound]
    val gid = found.asInstanceOf[GameFound].game
    (manager ? MakeAMove(gid, player1, Red, ForwardOneField)).futureValue mustBe a[NotYourTurn.type]

    manager ? FindGameForPlayer(player2)
    eventually {
      msg.gamesStarted.length mustBe 1
    }
    (manager ? MakeAMove(gid, player1, Green, ForwardOneField)).futureValue mustBe a[Moved.type]
  }
}
