package io.scalac.recru.bots

import akka.actor.Status.Success
import akka.actor.{Actor, ActorLogging, Props}
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import spray.json.{JsObject, JsString, JsonParser}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure => TryFailure, Success => TrySuccess, Try}
import scala.util.control.NonFatal

object HttpComms {
  case class ConnectedToGame(game: String, color: Option[Color.Value], listenOn: String)
}

trait HttpComms {
  import HttpComms._
  def connect(nameToUse: String): Future[ConnectedToGame]
}

class PlayHttpComms(baseUrl: String)
                   (implicit mat: Materializer, ec: ExecutionContext) extends HttpComms {
  import HttpComms._
  import play.api.libs.ws.DefaultBodyWritables._

  val wsClient = StandaloneAhcWSClient()

  override def connect(nameToUse: String): Future[HttpComms.ConnectedToGame] = {
    val url = baseUrl + "game/"
    // TODO: get proper parsing in place
    val b = JsObject("name" -> JsString(nameToUse)).prettyPrint

    println(s"Calling ${baseUrl + "game/"}")
    wsClient.url(url).addHttpHeaders("Content-Type" -> "application/json").post(b).filter(_.status == 200).flatMap { r =>
      JsonParser(r.body) match {
        case JsObject(fields) =>
          val g = ConnectedToGame(
            fields("gameId").toString(),
            fields.get("color").flatMap(js => Color.withNameOpt(js.toString())),
            r.header("x-listen-on").get)
          Future.successful(g)
        case _ =>
          Future.failed(new Exception("Cannot parse " + r.body))
      }
    }
  }
}


object RunnerPlayer {
  def props(usedName: String, client: HttpComms)
           (implicit mat: Materializer): Props =
    Props(new RunnerPlayer(usedName, client)(mat))
}

object Color extends Enumeration {
  type Color = Value
  val Yellow, Orange, Red, blue, Green, Purple = Value
  def withNameOpt(s: String): Option[Color] = values.find(_.toString == s)
}

object RunnerPlayerInternal {
  case object ConnectToGame
  case class ListenForGameEvents(game: String, color: Option[Color.Value], listenOn: String)

  sealed trait Observation //something did change on the play field
  case object InvalidEvent extends Observation //TODO: handle this on parsing level
  case class GameStarted() extends Observation
  case class NewTurnStarted() extends Observation
  case class GameUpdated() extends Observation
  case class GameDidEnd() extends Observation
}

//a simple player behaviour where it takes his color and always goes forward
class RunnerPlayer(usedName: String, client: HttpComms)
                  (implicit mat: Materializer) extends Actor with ActorLogging {
  import RunnerPlayerInternal._

  implicit val ec = context.dispatcher

  val rawConfig = ConfigFactory.load().getConfig("akka.kafka.consumer")
  val consumerSettings =
    ConsumerSettings(rawConfig, new StringDeserializer, new StringDeserializer)
      .withBootstrapServers("localhost:9092")
      .withGroupId(self.path.toString)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
      .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000")

  context.system.scheduler.scheduleOnce(1.second){
    println("Going to connect to the game!")
    self ! ConnectToGame
  }

  // refactor into 2 states!
  def handleMessages(gameId: Option[String], color: Option[Color.Value], currentListener: Option[Control]): Receive = {
    case ConnectToGame =>
      log.info("Connecting to the game!")
      client
        .connect(usedName)
        .map(g => self ! ListenForGameEvents(g.game, g.color, g.listenOn))
        .recover {
          case NonFatal(ex) =>
            log.error(ex,"Failed joining with {}", ex.getMessage)
            context.system.scheduler.scheduleOnce(10.second)(self ! ConnectToGame) // retry in a moment
        }

    case ListenForGameEvents(game, color, listenOn) =>
      log.info("{} Connected to {}, listening on {}", usedName, game, listenOn)
      val listenerControl: Control = buildListener(listenOn)
      val newState = handleMessages(Option(game), color, Option(listenerControl))
      context.become(newState)

    case InvalidEvent =>
      // do nothing
  }

  override def receive: Receive = handleMessages(None, None, None)

  private def buildListener(listenOn: String) = {
    val (consumerControl, _) =
      Consumer
        .plainSource(consumerSettings,
          Subscriptions.assignmentWithOffset(
            new TopicPartition(listenOn, 0) -> 0L
          ))
        .map(msg => self ! parseForProcessing(msg.value()))
        .recover{
          case NonFatal(ex) =>
            log.error(ex, "Kafka failed")
            InvalidEvent
        }
        .toMat(Sink.ignore)(Keep.both)
        .run()
    consumerControl
  }

  def parseForProcessing(rawKafkaValue: String): Observation = {
    val updateT = Try{ JsonParser(rawKafkaValue).asJsObject }
    log.info(s"Observed ${updateT} on Kafka")

    updateT match {
      case TrySuccess(js: JsObject) =>
        val fields = js.fields.mapValues(_.toString())
        parseFields(fields)
      case TryFailure(ex) =>
        log.error(ex, "Not a valid observation {}", ex.getMessage)
        InvalidEvent
    }
  }

  def parseFields(fields: Map[String, String]): Observation = {
    fields.get("type") match {
      case Some("game-start") =>
        InvalidEvent
      case Some("game-new-turn") =>
        InvalidEvent
      case Some("game-update") =>
        InvalidEvent
      case Some("game-end") =>
        InvalidEvent
      case _ =>
        InvalidEvent
    }
  }
}
