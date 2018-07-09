package io.scalac.recru

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

object Protocol extends SprayJsonSupport with DefaultJsonProtocol {
  case class IncomingPlayer(name: String)
  implicit val IncomingPlayerFormat = jsonFormat1(IncomingPlayer)

  //TODO: how should users be authenticated?
  case class IncomingMove(name: String, color: String, move: Int)
  implicit val IncomingMoveFormat = jsonFormat3(IncomingMove)
}
