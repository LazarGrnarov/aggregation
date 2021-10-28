package utils

import models.Data.{Signal, VehicleState}

object Utils {


  object Random {

  }

  def convertState(data: Map[String, Map[String, Double]]) = {
    data.map { case (k, v) =>
      k -> v.map {
        case (kk, vv) if kk == Signal.vehicleState.toString =>
          kk -> VehicleState(vv.toInt)
        case (kk, vv) if kk == Signal.recordedAt.toString || kk == Signal.avgSpeedRecordedAt.toString =>
          kk -> vv.toLong.toString
        case (kk, vv) if kk == Signal.isCharging.toString =>
          kk -> (vv == 1).toString
        case (kk, vv) => kk -> Math.round(vv * 100.0) / 100.0 // 2 decimals
      }
    }
  }
}
