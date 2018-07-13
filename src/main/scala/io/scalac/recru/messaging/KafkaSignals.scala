package io.scalac.recru.messaging

import akka.Done
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.{Materializer}
import akka.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory
import io.scalac.recru.Model.{Color, GameId, Move, Player}
import io.scalac.recru.messaging.Signals.SignalListenLocation
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer

class KafkaSink(implicit val mat: Materializer) {

  val listenLocation = SignalListenLocation("colloseum")

  val config = ConfigFactory.load().getConfig("akka.kafka.producer")
  val producerSettings =
    ProducerSettings(config, new StringSerializer, new StringSerializer)
      .withBootstrapServers("localhost:9092")

  //TODO: ...
  def foo() = {
    Source(1 to 100)
      .map(_.toString)
      .map(value => new ProducerRecord[String, String](listenLocation.v, value))
      .runWith(Producer.plainSink(producerSettings))
  }

}

class KafkaSignals(sink: KafkaSink) extends Signals {
  // Note: Signals now support a multi-tenant mode only (1 stream, many games), but could easily be moved to single tenant
  override val listenLocation = sink.listenLocation

  override def signalGameStart(gameId: GameId, players: Set[Player]): Done = {
    sink.foo()
    println(s"Started a game ${gameId} with ${players}")
    Done
  }
  override def signalTurn(gameId: GameId, playerMovingNow: Player): Done = {
    println(s"Game ${gameId} now waits on move from ${playerMovingNow}")
    Done
  }
  // Note: it would be a more useful design to include the whole state of board in the message, but it's intentionally made harder
  override def signalGameUpdate(gameId: GameId, player: Player, movedColor: Color, move: Move): Done = {
    println(s"Updated game ${gameId} with ${player}, ${move} for ${movedColor}")
    Done
  }

  override def signalGameEnd(gameId: GameId, winners: Seq[Player], losers: Seq[Player]): Done = {
    println(s"Game ${gameId} ended ${winners} losers ${losers}")
    Done
  }
}