package io.scalac.recru.messaging

import io.scalac.recru.Model._
import io.scalac.recru.messaging.Signals.SignalListenLocation
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, MustMatchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import spray.json.{JsArray, JsNumber, JsObject, JsString, JsValue}

import scala.concurrent.Future

class KafkaSignalsSpec extends FlatSpecLike with MustMatchers with BeforeAndAfterAll with Eventually with ScalaFutures {
  class FakeSink() extends SignalsSink {
    override val listenLocation = SignalListenLocation("some-kafka-topic")
    var seenMessages = List.empty[JsValue]

    override def pushToKafka(evt: JsValue): Future[Boolean] = {
      seenMessages = seenMessages :+ evt
      Future.successful(true)
    }
  }

  val game = GameId("game-1")
  val alice = Player("alice")
  val bob = Player("bob")

  "KafkaSignals" should "signal game start using the pre-defined format" in {
    val sink = new FakeSink()
    val kafka = new KafkaSignals(sink)

    kafka.signalGameStart(game, Set(alice, bob))

    sink.seenMessages mustBe Seq(
      JsObject(
        ("type", JsString("game-start")),
        ("gameId", JsString("game-1")),
        ("players", JsArray(
          JsString("alice"),
          JsString("bob")
        ))
      )
    )
  }

  it should "signal new turn using the pre-defined format" in {
    val sink = new FakeSink()
    val kafka = new KafkaSignals(sink)

    kafka.signalTurn(game, bob)
    kafka.signalTurn(game, alice)

    sink.seenMessages mustBe Seq(
      JsObject(
        ("type", JsString("game-new-turn")),
        ("gameId", JsString("game-1")),
        ("player", JsString("bob"))
      ),
      JsObject(
        ("type", JsString("game-new-turn")),
        ("gameId", JsString("game-1")),
        ("player", JsString("alice"))
      )
    )
  }

  it should "signal game update using the pre-defined format" in {
    val sink = new FakeSink()
    val kafka = new KafkaSignals(sink)

    kafka.signalGameUpdate(game, alice, Yellow, ForwardTwoFields)
    kafka.signalGameUpdate(game, alice, Orange, ForwardOneField)
    kafka.signalGameUpdate(game, bob, Red, BackOneField)
    kafka.signalGameUpdate(game, bob, Blue, BackTwoFields)

    sink.seenMessages mustBe Seq(
      JsObject(
        ("type", JsString("game-update")),
        ("gameId", JsString("game-1")),
        ("player", JsString("alice")),
        ("color", JsString("yellow")),
        ("move", JsNumber(2))
      ),
      JsObject(
        ("type", JsString("game-update")),
        ("gameId", JsString("game-1")),
        ("player", JsString("alice")),
        ("color", JsString("orange")),
        ("move", JsNumber(1))
      ),
      JsObject(
        ("type", JsString("game-update")),
        ("gameId", JsString("game-1")),
        ("player", JsString("bob")),
        ("color", JsString("red")),
        ("move", JsNumber(-1))
      ),
      JsObject(
        ("type", JsString("game-update")),
        ("gameId", JsString("game-1")),
        ("player", JsString("bob")),
        ("color", JsString("blue")),
        ("move", JsNumber(-2))
      )
    )
  }

  it should "signal game end using the pre-defined format" in {
    val sink = new FakeSink()
    val kafka = new KafkaSignals(sink)

    kafka.signalGameEnd(game, winners = Seq(alice), losers = Seq(bob))

    sink.seenMessages mustBe Seq(
      JsObject(
        ("type", JsString("game-end")),
        ("gameId", JsString("game-1")),
        ("winners", JsArray(
          JsString("alice")
        )),
        ("losers", JsArray(
          JsString("bob")
        ))
      )
    )
  }
}
