
import actors.{PersistenceActor, WatchActor}
import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.event.slf4j.Logger
import akka.pattern.ask
import akka.util.Timeout
import services.TelemetryService
import utils.Utils.convertState

import scala.concurrent.duration._


object Application extends App {

  implicit val actorSystem = ActorSystem("application")
  implicit val ec = actorSystem.dispatcher
  implicit val timeout: Timeout = 1.hour
  val logger = Logger("app")

  val persistenceActor = actorSystem.actorOf(Props[PersistenceActor])
  val watchActor = actorSystem.actorOf(Props[WatchActor])
  val watch = watchActor ? persistenceActor

  val telemetryService = new TelemetryService(persistenceActor)
  val graph = telemetryService.runBatch()
  val result = graph.mapMaterializedValue(_.map(convertState)).run()

  result.foreach(x => x.foreach { case (k, v) => println(s"$k -> $v") })
  result.onComplete(_ => persistenceActor ! PoisonPill)
  watch.onComplete(_ => actorSystem.terminate())
}
