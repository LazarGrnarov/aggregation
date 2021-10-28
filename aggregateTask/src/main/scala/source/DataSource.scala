package source

import akka.stream.scaladsl.{Merge, Source}
import models.Data.{DataPoint, Measure, Signal, Telemetry}
import utils.ApplicationConfig

import scala.concurrent.duration._
import scala.util.Random

object DataSource {

  var drivingMap: Map[String, Double] = (0 to 20).map(x => s"v_$x" -> 0.0).toMap
  var distMap: Map[String, Double] = (0 to 20).map(x => s"v_$x" -> 0.0).toMap

  def generateSources(elems: Seq[Seq[Telemetry]]) = {

    val sources = elems.map { elems =>
      if (ApplicationConfig.App.throttle) {
        Source(elems)
          .throttle(ApplicationConfig.App.rate, 1.second)
      } else {
        Source(elems)
      }
    }
    Source.zipN(sources).mapConcat(identity)
  }

  def generateData(numberOfSources: Int = ApplicationConfig.App.numberOfSources): Seq[Seq[Telemetry]] = {
    (0 until numberOfSources)
      .map(n => (0 until 1000).map(_ => generateTelemetry(n)))
  }

  def generateTelemetry(x: Int = -1) = {
      Telemetry(vehicleID = s"v_$x", recordedAt = System.currentTimeMillis() - Random.nextInt(1000), signalValue = generateRandomSignal(s"v_$x"))
    }

  def generateRandomSignal(vehicleID: String): Map[String, Double] = {
    val isCharging: Boolean = Random.nextInt(10) >= 7
    val speed = (Random.nextDouble() * 100) * { // km/h
      if (isCharging) 0 else 1
    } * Random.nextInt(2) // we want some cars (50%) that are not charging with speed 0
    val prevTime = drivingMap(vehicleID)
    val prevDist = distMap(vehicleID)
    val addedTime = (Random.nextInt(1000) + 1).toDouble * (if (speed > 0) 1 else 0) // in millis
    val drivingTime = prevTime + addedTime // in millis
    val distance = prevDist + (addedTime / 1000.0 * speed / 3.6) // meters
    Map(
      Signal.currentSpeed.toString -> speed,
      Signal.odometer.toString -> distance,
      Signal.drivingTime.toString -> drivingTime,
      Signal.isCharging.toString -> {
        if (isCharging) 1.0 else 0.0
      }
    )
  }

  def convert(t: Telemetry): Seq[DataPoint] = {
    val avgSpeedDataPoint: Option[DataPoint] = if (
      t.signalValue.contains(Signal.odometer.toString) && t.signalValue(Signal.odometer.toString) > 0 &&
        t.signalValue.contains(Signal.drivingTime.toString)) {
      val dist = t.signalValue(Signal.odometer.toString)
      val time = t.signalValue(Signal.drivingTime.toString)
      val avg = dist * 3.6 / (time / 1000)
      Some(DataPoint(vehicleID = t.vehicleID, recordedAt = t.recordedAt, measure = Measure(Signal.avgSpeed.toString, avg)))
    } else None
    t.signalValue.toSeq.map { case (k, v) =>
      Some(DataPoint(vehicleID = t.vehicleID, recordedAt = t.recordedAt, measure = Measure(k, v)))
    } ++ Seq(avgSpeedDataPoint)
  }.flatten
}
