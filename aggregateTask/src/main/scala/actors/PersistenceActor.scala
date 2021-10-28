package actors

import akka.actor.{Actor, ActorLogging}
import akka.event.{Logging, LoggingReceive}
import akka.event.slf4j.Logger
import models.Data.JsonFormatters._
import models.Data.Telemetry
import play.api.libs.json.Json
import utils.ApplicationConfig

import java.io.{File, PrintWriter}


class PersistenceActor extends Actor with ActorLogging {

  var file: File = _
  var printWriter: PrintWriter = _
  val logger = Logging(context.system, this)

  override def preStart(): Unit = {
    super.preStart()
    file = new File(ApplicationConfig.App.Persistence.path)
    printWriter = new PrintWriter(file)
  }

  override def receive: Receive = LoggingReceive {
    case t:Telemetry if ApplicationConfig.App.Persistence.enabled =>
      // can be used in more than one manner, we replicate persistence of raw data
      // many implementations are possible, saving to a db or a file based persistence,
      // spitting based on timestamp, vehicleID, telemetry data point
      printWriter.write(Json.stringify(Json.toJson(t)))
      printWriter.write("\n")
    case _ =>
  }

  override def postStop(): Unit = {
    printWriter.close()
    super.postStop()
  }
}
