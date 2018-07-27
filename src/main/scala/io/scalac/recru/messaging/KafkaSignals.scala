package io.scalac.recru.messaging

import akka.Done
import akka.kafka.{ProducerMessage, ProducerSettings}
import akka.kafka.scaladsl.Producer
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.typesafe.config.ConfigFactory
import io.scalac.recru.Model.{Color, GameId, Move, Player}
import io.scalac.recru.messaging.Signals.SignalListenLocation
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import spray.json.{JsArray, JsNumber, JsObject, JsString, JsValue}

import scala.concurrent.Future
import scala.util.control.NonFatal

class KafkaSink(implicit val mat: Materializer) {

  val listenLocation = SignalListenLocation("colloseum")

  val config = ConfigFactory.load().getConfig("akka.kafka.producer")
  val producerSettings =
    ProducerSettings(config, new StringSerializer, new StringSerializer)
      .withBootstrapServers("127.0.0.1:9092")

  val bufferSize = 1000

  val publishToKafka = Source.queue[JsValue](bufferSize, OverflowStrategy.backpressure)
    .map(_.prettyPrint) // we print pretty to make debugging easier
    .map{ v =>
      //TODO: the "game-all" key should be replaced with a gameId, this way we could have strong order per game not per all messages
      ProducerMessage.Message(
        new ProducerRecord[String, String](listenLocation.v, "game-all", v),
        1
      )
    }
    .via(Producer.flexiFlow(producerSettings))
    .recover {
      case NonFatal(ex) =>
        println(s"Can't send events to Kafka due to: ${ex.getMessage}")
    }
    .toMat(Sink.ignore)(Keep.left)
    .run()

  def pushToKafka(evt: JsValue): Future[Boolean] = {
    import mat.executionContext

    publishToKafka.offer(evt).map {
      case QueueOfferResult.Enqueued =>
        true
      case QueueOfferResult.Dropped | QueueOfferResult.QueueClosed =>
        false
      case QueueOfferResult.Failure(ex) =>
        println(s"Offer failed ${ex.getMessage}")
        false
    }
  }

}

private[messaging] object KafkaMarshalling {
  def marshallGameStart(gameId: GameId, players: Set[Player]): JsValue = {
    val playersV = players.map(p => JsString(p.name)).toVector
    JsObject(
      ("type", JsString("game-start")),
      ("gameId", JsString(gameId.v)),
      ("players", JsArray(playersV))
    )
  }

  def marshallTurn(gameId: GameId, playerMovingNow: Player): JsValue = {
    JsObject(
      ("type", JsString("game-new-turn")),
      ("gameId", JsString(gameId.v)),
      ("player", JsString(playerMovingNow.name))
    )
  }

  def marshallGameUpdate(gameId: GameId, player: Player, movedColor: Color, move: Move): JsValue = {
    JsObject(
      ("type", JsString("game-update")),
      ("gameId", JsString(gameId.v)),
      ("player", JsString(player.name)),
      ("color", JsString(movedColor.toString)),
      ("move", JsNumber(move.moveValue))
    )
  }

  def marshallGameEnd(gameId: GameId, winners: Seq[Player], losers: Seq[Player]): JsValue = {
    val winnersV = winners.map(p => JsString(p.name)).toVector
    val losersV = losers.map(p => JsString(p.name)).toVector
    JsObject(
      ("type", JsString("game-end")),
      ("gameId", JsString(gameId.v)),
      ("winners", JsArray(winnersV)),
      ("losers", JsArray(losersV))
    )
  }
}

class KafkaSignals(sink: KafkaSink) extends Signals {
  import KafkaMarshalling._
  // Note: Signals now support a multi-tenant mode only (1 stream, many games), but could easily be moved to single tenant
  override val listenLocation = sink.listenLocation

  override def signalGameStart(gameId: GameId, players: Set[Player]): Done = {
    //TODO: return type of push* is disconnected from the signal* methods, this could lead to kafka signals gone "missing" in case of backpressure
    sink.pushToKafka(marshallGameStart(gameId, players))
    println(s"Started a game ${gameId} with ${players}")
    Done
  }
  override def signalTurn(gameId: GameId, playerMovingNow: Player): Done = {
    sink.pushToKafka(marshallTurn(gameId, playerMovingNow))
    println(s"Game ${gameId} now waits on move from ${playerMovingNow}")
    Done
  }
  // Note: it would be a more useful design to include the whole state of board in the message, but it's intentionally made harder
  override def signalGameUpdate(gameId: GameId, player: Player, movedColor: Color, move: Move): Done = {
    sink.pushToKafka(marshallGameUpdate(gameId, player, movedColor, move))
    println(s"Updated game ${gameId} with ${player}, ${move} for ${movedColor}")
    Done
  }

  override def signalGameEnd(gameId: GameId, winners: Seq[Player], losers: Seq[Player]): Done = {
    sink.pushToKafka(marshallGameEnd(gameId, winners, losers))
    println(s"Game ${gameId} ended ${winners} losers ${losers}")
    Done
  }
}