import actors.PersistenceActor
import akka.actor.{ActorSystem, Props}
import akka.stream.scaladsl.Sink
import models.Data.{Signal, Telemetry, VehicleState}
import org.scalactic.Tolerance.convertNumericToPlusOrMinusWrapper
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.{convertToAnyShouldWrapper, equal}
import services.TelemetryService
import source.DataSource
import source.DataSource.convert
import utils.ApplicationConfig
import utils.Utils.convertState

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class aggregateTest extends AnyFlatSpec {
  implicit val system = ActorSystem("test")
  implicit val ec = system.dispatcher
  val data = DataSource.generateData(5)
  val ts = System.currentTimeMillis()
  val persistenceActor = system.actorOf(Props[PersistenceActor])
  val telemetryService = new TelemetryService(persistenceActor)

  val signalValue = Map(
    Signal.recordedAt.toString -> (ts.toDouble - 100),
    Signal.isCharging.toString -> 0.0,
    Signal.currentSpeed.toString -> 10.0,
    Signal.odometer.toString -> 100.0
  )
  val item = Seq(Seq(Telemetry(vehicleID = "v_1", ts, signalValue)))
  val items = Seq(
    Seq(Telemetry(vehicleID = "v_1", ts, signalValue), Telemetry(vehicleID = "v_1", ts, signalValue)),
    Seq(Telemetry(vehicleID = "v_2", ts, signalValue), Telemetry(vehicleID = "v_2", ts, signalValue)),
    Seq(Telemetry(vehicleID = "v_3", ts, signalValue), Telemetry(vehicleID = "v_3", ts, signalValue)))
  "Data" should "have 5 collections" in {
    assert(5 == data.size)
  }
  "Data" should "have 1000 items" in {
    assert(data.forall(_.size == 1000))
  }

  "Source" should "match data" in {
    val count = Await.result(DataSource.generateSources(item).map(_ => 1).runWith(Sink.fold(0)(_ + _)), Duration.Inf)
    assert(count == item.flatten.size)
    val countMany = Await.result(DataSource.generateSources(items).map(_ => 1).runWith(Sink.fold(0)(_ + _)), Duration.Inf)
    assert(countMany == items.flatten.size)
    val matData = Await.result(DataSource.generateSources(items).runWith(Sink.seq), Duration.Inf)
    assert(matData.forall(x => items.flatten.contains(x)))
  }

  "Source" should "convert data" in {
    //    DataSource.
    val convertedItem = convert(item.flatten.head)
    val convertedSource = DataSource.generateSources(item).mapConcat(convert)
    val count = Await.result(convertedSource.map(_ => 1).runWith(Sink.fold(0)(_ + _)), Duration.Inf)
    assert(count == convertedItem.size)
    val convertedSourceItem = Await.result(convertedSource.runWith(Sink.seq), Duration.Inf)
    assert(convertedItem.map(x => x.measure.name).toSet.subsetOf(convertedSourceItem.map(x => x.measure.name).toSet))
  }

  "TelemetryService" should "aggregate generated data" in {
    val result = Await.result(telemetryService.runBatch().run(), Duration.Inf)
    assert(result.keySet.size == ApplicationConfig.App.numberOfSources)
    assert(result.values.forall(_.nonEmpty))

    convertState(result).map { case (k, v) => println(s"$k -> $v") }
  }

  "TelemetryService" should "aggregate number of charges data" in {

    val telemetry = Seq(
      Telemetry(vehicleID = "v_1", ts, Map(Signal.isCharging.toString -> 0.0)),
      Telemetry(vehicleID = "v_1", ts + 1, Map(Signal.isCharging.toString -> 1.0)),
      Telemetry(vehicleID = "v_1", ts + 2, Map(Signal.isCharging.toString -> 0.0)),
      Telemetry(vehicleID = "v_1", ts + 3, Map(Signal.isCharging.toString -> 1.0)),
    )
    val telemetry2 = Seq(
      Telemetry(vehicleID = "v_1", ts, Map(Signal.isCharging.toString -> 0.0)),
      Telemetry(vehicleID = "v_1", ts + 1, Map(Signal.isCharging.toString -> 1.0)),
      Telemetry(vehicleID = "v_1", ts + 2, Map(Signal.isCharging.toString -> 1.0)),
      Telemetry(vehicleID = "v_1", ts + 3, Map(Signal.isCharging.toString -> 1.0)),
    )
    val result = Await.result(telemetryService.runBatch(dataOpt = Some(Seq(telemetry))).run(), Duration.Inf)
    assert(result("v_1")(Signal.chargeCount.toString) == 2)

    val result2 = Await.result(telemetryService.runBatch(dataOpt = Some(Seq(telemetry2))).run(), Duration.Inf)
    assert(result2("v_1")(Signal.chargeCount.toString) == 1)
  }

  "TelemetryService" should "filter based on time range" in {
    val telemetry = Seq(
      Telemetry(vehicleID = "v_1", 100, Map(Signal.isCharging.toString -> 0.0)),
      Telemetry(vehicleID = "v_2", 105, Map(Signal.isCharging.toString -> 1.0)),
      Telemetry(vehicleID = "v_3", 44, Map(Signal.isCharging.toString -> 0.0)),
      Telemetry(vehicleID = "v_4", 222, Map(Signal.isCharging.toString -> 1.0)),
      Telemetry(vehicleID = "v_5", 1222, Map(Signal.isCharging.toString -> 1.0)),
    )

    val result = Await.result(telemetryService.runBatch(from = 100, to = 1000, dataOpt = Some(Seq(telemetry))).run(), Duration.Inf)
    assert(result.contains("v_1"))
    assert(result.contains("v_2"))
    assert(!result.contains("v_3"))
    assert(result.contains("v_4"))
    assert(!result.contains("v_5"))
  }


  "TelemetryService" should "aggregate vehicle state" in {
    val telemetry = Seq(
      Telemetry(vehicleID = "v_1", ts, Map(Signal.isCharging.toString -> 0.0)),
      Telemetry(vehicleID = "v_1", ts + 1, Map(Signal.isCharging.toString -> 1.0)),
      Telemetry(vehicleID = "v_1", ts + 2, Map(Signal.isCharging.toString -> 0.0)),
      Telemetry(vehicleID = "v_1", ts + 3, Map(Signal.isCharging.toString -> 1.0)),
    ) ++ Seq(
      Telemetry(vehicleID = "v_2", ts, Map(Signal.isCharging.toString -> 0.0)),
      Telemetry(vehicleID = "v_2", ts + 1, Map(Signal.isCharging.toString -> 1.0)),
      Telemetry(vehicleID = "v_2", ts + 2, Map(Signal.isCharging.toString -> 1.0)),
      Telemetry(vehicleID = "v_2", ts + 3, Map(Signal.isCharging.toString -> 1.0)),
      Telemetry(vehicleID = "v_2", ts + 4, Map(Signal.isCharging.toString -> 0.0)),
    ) ++ Seq(
      Telemetry(vehicleID = "v_3", ts, Map(Signal.currentSpeed.toString -> 100)),
      Telemetry(vehicleID = "v_3", ts + 1, Map(Signal.currentSpeed.toString -> 50)),
      Telemetry(vehicleID = "v_3", ts + 4, Map(Signal.currentSpeed.toString -> 0.0)),
    ) ++ Seq(
      Telemetry(vehicleID = "v_4", ts, Map(Signal.currentSpeed.toString -> 100)),
      Telemetry(vehicleID = "v_4", ts + 1, Map(Signal.currentSpeed.toString -> 0)),
      Telemetry(vehicleID = "v_4", ts + 4, Map(Signal.currentSpeed.toString -> 100)),
    ) ++ Seq(
      Telemetry(vehicleID = "v_5", ts, Map(Signal.currentSpeed.toString -> 100)),
      Telemetry(vehicleID = "v_5", ts + 4, Map(Signal.isCharging.toString -> 1.0, Signal.currentSpeed.toString -> 0.0)),
      Telemetry(vehicleID = "v_5", ts + 5, Map(Signal.isCharging.toString -> 1.0, Signal.currentSpeed.toString -> 0.0))
    )

    val result = Await.result(telemetryService.runBatch(dataOpt = Some(Seq(telemetry))).run(), Duration.Inf)
    assert(result("v_1")(Signal.vehicleState.toString) == VehicleState.Charging.id)
    assert(result("v_2")(Signal.vehicleState.toString) == VehicleState.Unknown.id)
    assert(result("v_3")(Signal.vehicleState.toString) == VehicleState.Parked.id)
    assert(result("v_4")(Signal.vehicleState.toString) == VehicleState.Driving.id)
    assert(result("v_5")(Signal.vehicleState.toString) == VehicleState.Charging.id) // should not be parked
  }

  "TelemetryService" should "calculate average speed" in {
    val telemetry = Seq(
      Telemetry(vehicleID = "v_2", ts + 4, Map(Signal.drivingTime.toString -> 5000, Signal.odometer.toString -> 20)),
      Telemetry(vehicleID = "v_1", ts + 5, Map(Signal.drivingTime.toString -> 10000, Signal.odometer.toString -> 100))
    )
    val result = Await.result(telemetryService.runBatch(dataOpt = Some(Seq(telemetry))).run(), Duration.Inf)
    assert(result("v_1")(Signal.avgSpeed.toString) == 36)
    result("v_2")(Signal.avgSpeed.toString) should equal(4 * 3.6 +- 0.5)
  }

  "TelemetryService" should "calculate max speed" in {
    val telemetry = Seq(
      Telemetry(vehicleID = "v_4", ts, Map(Signal.currentSpeed.toString -> 100)),
      Telemetry(vehicleID = "v_4", ts + 1, Map(Signal.currentSpeed.toString -> 0)),
      Telemetry(vehicleID = "v_4", ts + 4, Map(Signal.currentSpeed.toString -> 100)),
      Telemetry(vehicleID = "v_1", ts + 4, Map(Signal.currentSpeed.toString -> 200)),
      Telemetry(vehicleID = "v_3", ts + 2, Map(Signal.currentSpeed.toString -> 250)),
      Telemetry(vehicleID = "v_2", ts, Map(Signal.currentSpeed.toString -> 0)),
      Telemetry(vehicleID = "v_2", ts + 4, Map(Signal.currentSpeed.toString -> 75)),
      Telemetry(vehicleID = "v_2", ts + 4, Map(Signal.currentSpeed.toString -> 0)),
      Telemetry(vehicleID = "v_3", ts + 3, Map(Signal.currentSpeed.toString -> 0)),
      Telemetry(vehicleID = "v_5", ts + 4, Map(Signal.currentSpeed.toString -> 0)),
      Telemetry(vehicleID = "v_5", ts + 5, Map(Signal.currentSpeed.toString -> 0)),
      Telemetry(vehicleID = "v_5", ts + 5, Map(Signal.currentSpeed.toString -> 0)),
    )
    val result = Await.result(telemetryService.runBatch(dataOpt = Some(Seq(telemetry))).run(), Duration.Inf)
    assert(result("v_1")(Signal.maxSpeed.toString) == 200)
    assert(result("v_2")(Signal.maxSpeed.toString) == 75)
    assert(result("v_3")(Signal.maxSpeed.toString) == 250)
    assert(result("v_4")(Signal.maxSpeed.toString) == 100)
    assert(result("v_5")(Signal.maxSpeed.toString) == 0)
  }

  "TelemetryService" should "calculate vehicle statistic" in {
    val telemetry = Seq(
      Telemetry(vehicleID = "v_4", ts, Map(Signal.currentSpeed.toString -> 100)),
      Telemetry(vehicleID = "v_4", ts + 1, Map(Signal.currentSpeed.toString -> 0)),
      Telemetry(vehicleID = "v_4", ts + 4, Map(Signal.currentSpeed.toString -> 100)),
      Telemetry(vehicleID = "v_1", ts + 4, Map(Signal.currentSpeed.toString -> 200)),
      Telemetry(vehicleID = "v_3", ts + 2, Map(Signal.currentSpeed.toString -> 250)),
      Telemetry(vehicleID = "v_2", ts, Map(Signal.currentSpeed.toString -> 0)),
      Telemetry(vehicleID = "v_2", ts + 4, Map(Signal.currentSpeed.toString -> 75)),
      Telemetry(vehicleID = "v_2", ts + 4, Map(Signal.currentSpeed.toString -> 0)),
      Telemetry(vehicleID = "v_3", ts + 3, Map(Signal.currentSpeed.toString -> 0)),
      Telemetry(vehicleID = "v_5", ts + 4, Map(Signal.currentSpeed.toString -> 0)),
      Telemetry(vehicleID = "v_5", ts + 5, Map(Signal.currentSpeed.toString -> 0)),
      Telemetry(vehicleID = "v_5", ts + 5, Map(Signal.currentSpeed.toString -> 0)),
    )

    val result = Await.result(telemetryService.getAggregationByIDs(Seq("v_1", "v_2"), dataOpt = Some(Seq(telemetry))), Duration.Inf)
    assert(result.nonEmpty)
    assert(result.contains("v_1"))
    assert(result.contains("v_2"))

    val result2 = Await.result(telemetryService.getAggregationByID("v_2", dataOpt = Some(Seq(telemetry))), Duration.Inf)
    assert(result2.nonEmpty)
    assert(result2.contains(Signal.maxSpeed.toString))
    assert(result2(Signal.maxSpeed.toString) == 75)
  }
}
