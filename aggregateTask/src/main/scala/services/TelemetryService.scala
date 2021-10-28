package services

import akka.actor.{ActorRef, ActorSystem}
import akka.event.slf4j.Logger
import akka.event.{Logging, LoggingAdapter}
import akka.stream.scaladsl.{Flow, Keep, Sink}
import models.Data.{Signal, Telemetry, VehicleState}
import source.DataSource.{convert, generateData, generateSources}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class TelemetryService(persistenceActor: ActorRef)(implicit system: ActorSystem) {
  implicit val ec = system.dispatcher
  val logger = Logger(this.getClass.getSimpleName)
  implicit val adapter: LoggingAdapter = Logging(system, "customLogger")

  def stateTransition(currentState: VehicleState.Value, updatedState: VehicleState.Value): VehicleState.Value = {
    currentState match {
      case VehicleState.Driving | VehicleState.Parked =>
        updatedState match {
          case VehicleState.Charging =>
            updatedState
          case _ => updatedState
        }
      case VehicleState.Charging | VehicleState.Unknown =>
        updatedState
    }
  }

  def persist(t: Telemetry): Telemetry = {
    // Just to generate a flow, this can be done with graph fan-out or number of other streaming concepts
    persistenceActor ! t
    t
  }

  def stateTransition(current: Double, updated: Double): VehicleState.Value = stateTransition(VehicleState(current.toInt), VehicleState(updated.toInt))

  def runBatch(from: Long = -1L, to: Long = Long.MaxValue, dataOpt: Option[Seq[Seq[Telemetry]]] = None) = {
    logger.debug(s"from=$from, to=$to")
    val data = dataOpt.getOrElse(generateData())
    val source = generateSources(data)
    source
      .via(Flow.fromFunction(persist)) // persist the raw data for future use/checks/etc.
      .groupedWithin(Int.MaxValue, 3.seconds) // for logging purposes, the timer can be configured
      .log(name = "telemetrySource", e => s"items counted: ${e.size}")
      .mapConcat(identity) // flatten the stream
      .mapConcat(convert) // convert it to individual measure item(s)
      .filter(d => d.recordedAt <= to && d.recordedAt >= from) // we have to filter an "infinite" stream
      .toMat(Sink.fold(zero = Map.empty[String, Map[String, Double]]) {
        case (acc, point) =>
          val currentMap = acc.getOrElse(point.vehicleID, Map.empty[String, Double])
          val recordedAt: Double = currentMap.getOrElse(Signal.recordedAt.toString, 0)
          val updatedRecordAt: Double = if (point.recordedAt > recordedAt.toLong) point.recordedAt else recordedAt
          val vehicleMap: Map[String, Double] = currentMap ++ Map(Signal.recordedAt.toString -> updatedRecordAt)
          val avgSpeedRecordedAt: Double = vehicleMap.getOrElse(Signal.avgSpeedRecordedAt.toString, 0)
          val vehicleState: Double = vehicleMap.getOrElse(Signal.vehicleState.toString, 0)
          point.measure.name match {
            // if we already have a later point it is more accurate data
            case s: String if s == Signal.avgSpeed.toString && point.recordedAt > avgSpeedRecordedAt =>
              val updatedMap: Map[String, Double] = vehicleMap ++ Map(
                Signal.avgSpeed.toString -> point.measure.value,
                Signal.avgSpeedRecordedAt.toString -> point.recordedAt
              )
              acc ++ Map(point.vehicleID -> updatedMap)
            case s: String if s == Signal.currentSpeed.toString =>
              val currentMaxSpeed = vehicleMap.getOrElse(Signal.maxSpeed.toString, -1.0)
              val maxSpeed = if (currentMaxSpeed < point.measure.value) point.measure.value else currentMaxSpeed
              val isParked: Double = if (point.measure.value > 0) VehicleState.Driving.id else VehicleState.Parked.id
              val updatedVehicleState = stateTransition(vehicleState, isParked)
              val updatedVehicleMap: Map[String, Double] = vehicleMap ++ Map(Signal.maxSpeed.toString -> maxSpeed) ++ {
                if (VehicleState.Charging.id == vehicleState && point.measure.value == 0) {
                  // Don't override charging
                  Map(Signal.vehicleState.toString -> vehicleState)
                } else {
                  Map(Signal.vehicleState.toString -> updatedVehicleState.id)
                }
              }
              acc ++ Map(point.vehicleID -> updatedVehicleMap)
            case s: String if s == Signal.isCharging.toString =>
              val chargeState = vehicleMap.getOrElse[Double](Signal.isCharging.toString, 0.0)
              val updatedState: Map[String, Double] =
                if (chargeState == 0 && point.measure.value == 1) {
                  val chargeCount = vehicleMap.getOrElse(Signal.chargeCount.toString, 0.0) + 1.0
                  Map(
                    Signal.isCharging.toString -> 1.0,
                    Signal.chargeCount.toString -> chargeCount,
                    Signal.vehicleState.toString -> stateTransition(vehicleState, VehicleState.Charging.id).id
                  )
                } else if (chargeState == 1 && point.measure.value == 0) {
                  Map(
                    Signal.isCharging.toString -> 0,
                    Signal.chargeCount.toString -> vehicleMap.getOrElse(Signal.chargeCount.toString, 0),
                    Signal.vehicleState.toString -> stateTransition(vehicleState, VehicleState.Unknown.id).id
                  )
                } else {
                  // No change, we are still either charging or not charging
                  Map.empty[String, Double]
                }
              acc ++ Map(point.vehicleID -> (vehicleMap ++ updatedState))
            case _ =>
              acc
          }
      })(Keep.right)
  }


  // Providing access to aggregations in a mock fashion, in real scenario a persistence layer will be added
  // so we don't have to do the aggregation on each call
  def getAggregationByID(vehicleID: String, from: Long = -1L, to: Long = Long.MaxValue, dataOpt: Option[Seq[Seq[Telemetry]]] = None) = {
    val graph = runBatch(from, to, dataOpt)
    val result = graph.run()
    result.map( _.getOrElse(vehicleID, Map.empty))
  }

  def getAggregationByIDs(vehicleIDs: Seq[String], from: Long = -1L, to: Long = Long.MaxValue, dataOpt: Option[Seq[Seq[Telemetry]]] = None) = {
    val graph = runBatch(from, to, dataOpt)
    val result = graph.run()
    result.map(_.filter(x => vehicleIDs.contains(x._1)))
//    Future.sequence(vehicleIDs.map(vehicleID => vehicleID -> result.map(_.get(vehicleID))).toMap)
  }
}
