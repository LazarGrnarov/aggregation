package models

import play.api.libs.json.Json

object Data {


  object VehicleState extends Enumeration {
    type State = Value

    val Unknown = Value(0)
    val Driving = Value(1)
    val Charging = Value(2)
    val Parked = Value(3)
  }

  object Signal extends Enumeration {
    type Signal = Value
    val chargeCount, vehicleState, isCharging, currentSpeed, maxSpeed, odometer, drivingTime, avgSpeed, avgSpeedRecordedAt, recordedAt = Value
  }

  case class Measure(name: String, value: Double)

  case class DataPoint(vehicleID: String, recordedAt: Long, measure: Measure)

  case class Telemetry(vehicleID: String, recordedAt: Long, signalValue: Map[String, Double])

  object JsonFormatters {
    implicit val measureFormat = Json.format[Measure]
    implicit val telemetryFormat = Json.format[Telemetry]
  }
}
