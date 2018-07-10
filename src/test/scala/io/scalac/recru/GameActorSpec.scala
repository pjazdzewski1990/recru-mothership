package io.scalac.recru

import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import io.scalac.recru.GameActor._
import io.scalac.recru.GameManagerActor.GameStarted
import io.scalac.recru.Model._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfter, FlatSpecLike, MustMatchers}

import scala.concurrent.duration._

class GameActorSpec extends TestKit(ActorSystem("GameActor"))
  with FlatSpecLike with MustMatchers with BeforeAndAfter with Eventually with ScalaFutures {

  implicit val timeout = Timeout(3, TimeUnit.SECONDS)
  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  val gameId = GameId("jumanji")
  val player1 = Player("p1")
  val player2 = Player("p2")
  val player3 = Player("p3")

  class FakeMessages extends Messages {
    override def listenLocation: String = ""

    var seenPlayers: Set[Player] = Set.empty
    override def signalGameStart(players: Set[Player]): Done = {
      seenPlayers = seenPlayers ++ players
      Done
    }

    var seenUpdates = Seq.empty[(GameId, Player, Color, Move)]
    override def signalGameUpdate(gameId: GameId, player: Player, color: Color, move: Move): Done = {
      seenUpdates = seenUpdates :+ (gameId, player, color, move)
      Done
    }
  }

  "GameActor" should "start a game with 2 players after the timeout" in {
    val messages = new FakeMessages
    val manager = TestProbe()
    val game = system.actorOf(GameActor.props(gameId, manager.ref, messages, playersWaitTimeout = 1.second))
    val joined1F = game ? JoinGame(player1)
    val joined2F = game ? JoinGame(player2)

    joined1F.futureValue mustBe a[Joined]
    joined2F.futureValue mustBe a[Joined]

    eventually {
      messages.seenPlayers mustBe Set(player1, player2)
    }
    manager.expectMsg(GameStarted(Set(player1, player2)))
  }

  it should "immediately start the game when having {MAX} players" in {
    val messages = new FakeMessages
    val manager = TestProbe()
    val game = system.actorOf(GameActor.props(gameId, manager.ref, messages, playersWaitTimeout = 99.hours))

    (1 to GameActor.maxPlayersInGame).map(x => Player(x.toString)).map{ p =>
      (game ? JoinGame(p)).futureValue mustBe a[Joined]
    }

    eventually {
      messages.seenPlayers.size mustBe GameActor.maxPlayersInGame
    }
    manager.expectMsgClass(GameStarted(Set.empty).getClass)
  }

  it should "reject players over the {MAX} players limit" in {
    val messages = new FakeMessages
    val manager = TestProbe()
    val game = system.actorOf(GameActor.props(gameId, manager.ref, messages, playersWaitTimeout = 99.hours))

    val players = (1 to GameActor.maxPlayersInGame * 2).map(x => Player(x.toString))

    players.take(GameActor.maxPlayersInGame).map{ p =>
      (game ? JoinGame(p)).futureValue mustBe a[Joined]
    }

    players.drop(GameActor.maxPlayersInGame).map{ p =>
      (game ? JoinGame(p)).futureValue mustBe a[GameIsAlreadyRunning.type]
    }
  }

  it should "end the game with a draw if nobody shows up" in {
    1 mustBe 2
  }

  //NOTE: there's a slight chance that this test might fail due to randomness
  it should "make sure that every player in a game has a distinct color and the order of color assignment is different game-to-game" in {
    val messages = new FakeMessages
    val manager = TestProbe()

    def getAssignedColors(): Seq[Color] = {
      val game = system.actorOf(GameActor.props(gameId, manager.ref, messages, playersWaitTimeout = 1.hour))
      (0 until GameActor.maxPlayersInGame).map {idx =>
        (game ? JoinGame(Player("p_" + idx.toString))).mapTo[Joined].futureValue.colorAssigned
      }
    }

    val colorsInRounds = for {
      _ <- 0 until 3
    } yield {
      val colors = getAssignedColors()
      colors.distinct.size mustBe colors.size // they are only distinct values
      colors
    }
    colorsInRounds.distinct.size mustBe colorsInRounds.size // there are only distinct combination
  }

  it should "reject players who want to join a running game" in {
    val messages = new FakeMessages
    val manager = TestProbe()
    val game = system.actorOf(GameActor.props(gameId, manager.ref, messages, playersWaitTimeout = 1.second))
    val joined1F = game ? JoinGame(player1)
    val joined2F = game ? JoinGame(player2)

    joined1F.futureValue mustBe a[Joined]
    joined2F.futureValue mustBe a[Joined]

    eventually {
      messages.seenPlayers mustBe Set(player1, player2)
    }
    manager.expectMsg(GameStarted(Set(player1, player2)))

    (game ? JoinGame(player2)).futureValue mustBe a[GameIsAlreadyRunning.type]
  }

  it should "allow players to submit moves according to the defined order" in {
    val messages = new FakeMessages
    val manager = TestProbe()
    val game = system.actorOf(GameActor.props(gameId, manager.ref, messages, playersWaitTimeout = 1.second))

    game ! JoinGame(player1)
    game ! JoinGame(player2)
    eventually {
      messages.seenPlayers mustBe Set(player1, player2)
    }

    (game ? PlayerMoves(player1, Red, ForwardTwoFields)).mapTo[MoveResult].futureValue mustBe a[Moved.type]
    eventually {
      messages.seenUpdates.length mustBe 1
      messages.seenUpdates.last mustBe (gameId, player1, Red, ForwardTwoFields)
    }

    (game ? PlayerMoves(player2, Green, ForwardOneField)).mapTo[MoveResult].futureValue mustBe a[Moved.type]
    eventually {
      messages.seenUpdates.length mustBe 2
      messages.seenUpdates.last mustBe (gameId, player2, Green, ForwardOneField)
    }

    (game ? PlayerMoves(player1, Green, BackOneField)).mapTo[MoveResult].futureValue mustBe a[Moved.type]
    eventually {
      messages.seenUpdates.length mustBe 3
      messages.seenUpdates.last mustBe (gameId, player1, Green, BackOneField)
    }

    (game ? PlayerMoves(player2, Purple, BackTwoFields)).mapTo[MoveResult].futureValue mustBe a[Moved.type]
    eventually {
      messages.seenUpdates.length mustBe 4
      messages.seenUpdates.last mustBe (gameId, player2, Purple, BackTwoFields)
    }
  }

  it should "reject player moves if provided out of order" in {
    val messages = new FakeMessages
    val manager = TestProbe()
    val game = system.actorOf(GameActor.props(gameId, manager.ref, messages, playersWaitTimeout = 1.second))

    game ! JoinGame(player1)
    game ! JoinGame(player2)
    eventually {
      messages.seenPlayers mustBe Set(player1, player2)
    }

    (game ? PlayerMoves(player2, Purple, ForwardTwoFields)).mapTo[MoveResult].futureValue mustBe a[NotYourTurn.type]
    (game ? PlayerMoves(player1, Purple, ForwardTwoFields)).mapTo[MoveResult].futureValue mustBe a[Moved.type]
    eventually {
      messages.seenUpdates.length mustBe 1
      messages.seenUpdates.last mustBe (gameId, player1, Purple,  ForwardTwoFields)
    }

    (game ? PlayerMoves(player1, Red, ForwardOneField)).mapTo[MoveResult].futureValue mustBe a[NotYourTurn.type]
    (game ? PlayerMoves(player2, Red, ForwardOneField)).mapTo[MoveResult].futureValue mustBe a[Moved.type]
    eventually {
      messages.seenUpdates.length mustBe 2
      messages.seenUpdates.last mustBe (gameId, player2, Red, ForwardOneField)
    }

    (game ? PlayerMoves(player2, Orange, BackOneField)).mapTo[MoveResult].futureValue mustBe a[NotYourTurn.type]
    (game ? PlayerMoves(player1, Orange, BackOneField)).mapTo[MoveResult].futureValue mustBe a[Moved.type]
    eventually {
      messages.seenUpdates.length mustBe 3
      messages.seenUpdates.last mustBe (gameId, player1, Orange, BackOneField)
    }
  }
}
