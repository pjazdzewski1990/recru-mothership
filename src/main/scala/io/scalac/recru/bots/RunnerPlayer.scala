package io.scalac.recru.bots

import akka.NotUsed
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
import spray.json.{JsArray, JsNumber, JsObject, JsString, JsValue, JsonParser}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Try, Failure => TryFailure, Success => TrySuccess}
import scala.util.control.NonFatal

object HttpComms {
  case class ConnectedToGame(game: String, color: Option[Color.Value], listenOn: String)
}

trait HttpComms {
  import HttpComms._
  def connect(nameToUse: String): Future[ConnectedToGame]
  def sendMove(playerName: String, gameId: String, color: Color.Value, move: Int): Future[NotUsed]
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

    wsClient.url(url).addHttpHeaders("Content-Type" -> "application/json").post(b).filter(_.status == 200).flatMap { r =>
      JsonParser(r.body) match {
        case JsObject(fields) =>
          val g = ConnectedToGame(
            fields.get("gameId").map{
              case JsString(v) => v
            }.get,
            fields.get("secretColor").flatMap{
              case JsString(v) => Color.withNameOpt(v)
            }.headOption,
            r.header("x-listen-on").get)

          Future.successful(g)
        case _ =>
          Future.failed(new Exception("Cannot parse " + r.body))
      }
    }
  }

  override def sendMove(playerName: String, gameId: String, color: Color.Value, move: Int): Future[NotUsed] = {
    val url = baseUrl + "game/" + gameId
    val b = JsObject(
      "name" -> JsString(playerName),
      "color" -> JsString(color.toString),
      "move" -> JsNumber(move)
    ).prettyPrint

    wsClient.url(url).addHttpHeaders("Content-Type" -> "application/json").post(b).filter(_.status == 200).map(_ => NotUsed)
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
  def withNameOpt(s: String): Option[Color] =
    values.find(_.toString.toLowerCase == s.toLowerCase)
}

object RunnerPlayerInternal {
  case object ConnectToGame
  case class ListenForGameEvents(game: String, color: Option[Color.Value], listenOn: String)

  sealed trait Observation //something did change on the play field
  case object InvalidEvent extends Observation //TODO: handle this on parsing level
  case object ForeignEvent extends Observation // an event from tha game that is not ours
  case class GameStarted() extends Observation
  case class NewTurnStarted(name: String) extends Observation
  case class GameUpdated() extends Observation
  case class GameDidEnd(winners: Seq[String]) extends Observation
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

  scheduleGameStart()

  private def scheduleGameStart() = {
    context.system.scheduler.scheduleOnce(1.second) {
      self ! ConnectToGame
    }
  }

  // refactor into 2 states!
  def playingGameReceive(gameId: String, color: Color.Value, currentListener: Control): Receive = {

    case NewTurnStarted(playerStartingTheTurn) if playerStartingTheTurn == usedName =>
      log.info("Player {} is doing his turn", usedName)
      client.sendMove(playerName = usedName, gameId = gameId, color, move = 1).map(_ => log.debug("Move was sent"))

    case GameDidEnd(winners) =>
      if(winners.contains(usedName)) {
        log.info("We did win {}", winners)
      } else {
        log.info("We didn't win {}" , winners)
      }
      scheduleGameStart()
      context.become(waitingForGameReceive())

    case _: GameStarted | _: NewTurnStarted | _: GameUpdated =>
      // do nothing

    case InvalidEvent =>
      // do nothing
  }

  def waitingForGameReceive(): Receive = {
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
      val listenerControl: Control = buildListener(listenOn, game)
      val newState = playingGameReceive(game, color.get, listenerControl)
      context.become(newState)
  }

  override def receive: Receive = waitingForGameReceive() 

  private def buildListener(listenOn: String, listenForGame: String) = {
    val parentActor = self

    val (consumerControl, _) =
      Consumer
        .plainSource(consumerSettings,
          Subscriptions.assignmentWithOffset(
            new TopicPartition(listenOn, 0) -> 0L
          ))
        .map(msg => parentActor ! parseForProcessing(msg.value(), listenForGame))
        .recover{
          case NonFatal(ex) =>
            log.error(ex, "Kafka failed")
            InvalidEvent
        }
        .toMat(Sink.ignore)(Keep.both)
        .run()
    consumerControl
  }

  def parseForProcessing(rawKafkaValue: String, listenForGame: String): Observation = {
    val updateT = Try{ JsonParser(rawKafkaValue).asJsObject }
    log.info(s"Observed ${updateT} on Kafka")

    updateT match {
      case TrySuccess(js: JsObject) =>
        val isForSameGame = parseGameId(js.fields).map(_.equalsIgnoreCase(listenForGame)).getOrElse(false)

        if(isForSameGame) {
          parseFields(js.fields)
        } else {
          ForeignEvent
        }
      case TryFailure(ex) =>
        log.error(ex, "Not a valid observation {}", ex.getMessage)
        InvalidEvent
    }
  }

  private val jsStringToString: JsValue => String = {
    case JsString(v) => v
    case _ => ""
  }

  private val jsArrayStringToStringList: JsValue => List[String] = {
    case JsArray(v) => v.map(jsStringToString).toList
    case _ => List.empty
  }

  //TODO: dude, you really need to wrap those types
  def parseGameId(fields: Map[String, JsValue]): Option[String] = {
    fields.get("gameId").map(jsStringToString)
  }

  def parseFields(fields: Map[String, JsValue]): Observation = {
    fields.get("type") match {
      case Some(JsString("game-start")) =>
        GameStarted()
      case Some(JsString("game-new-turn")) =>
        val player = fields.get("player").map(jsStringToString).getOrElse("")
        NewTurnStarted(player)
      case Some(JsString("game-update")) =>
        GameUpdated()
      case Some(JsString("game-end")) =>
        val winners: Seq[String] = fields
          .get("winners")
          .map(jsArrayStringToStringList)
          .getOrElse(Seq.empty)
        GameDidEnd(winners)
      case _ =>
        InvalidEvent
    }
  }
}
