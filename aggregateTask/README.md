## Aggregation Example

Proof of Concept application of a mock telemetry aggregation.

Logic done using akka, primarily streams, since they present a nice fit
for high frequency data (small in size).<br />
*Notes*: Some presumptions are taken into account when designing this.
 - Data is always correct (i.e no negative speed/distance)
 - Aggregation is done with an "until now" concept in mind.


The data is generated dynamically using `DataSource`. This decision was made
to mimic high data volume (the data itself should be valid, however this was not the focus).
For testing purposes static data was created to be able to control the scenario tested: <br />
Example: vehicleState change (parked -> driving)
<br />
<br />
The main aggregation logic is contained in `TelemetryService` class, using
filtering to mimic a batch operation on a stream. Current implementation 
assumes data ordering is important, if data is split up in such a way where data points are not dependent 
(ordering does not matter) it would be easy to switch to `foldAsync` combined with `ask` pattern actor(s).
<br />
<br />
Core logic is contained in `runBatch` method that returns a `runnableGraph` for easier debuuging manipulation (it can be run anywhere).
<br />
Couple of methods `getAggregationByIDs` and `getAggregationByID` were added for exposing direct access to aggregation. Calling either would
run the aggregation each time, so a real implementation would include some sort of storage (however this is outside the scope of the example).
<br />
<br />
*Note*: While a database storage is outside the scope, it would be easy to add a db Sink or a construct a custom db Flow at 
any point in the stream composition.

Rest of the code is mostly orchestration and coordination of the actors, to make sure the application does not
exit before all the data has been processed (This would not be an issue in a real world example where the app would be running constantly).

## How to run

`sbt run` to run the main example app. <br />
`sbt test` to run the tests. <br />
App configuration is in `application.conf` parameters can be configured from there.

### Params:

 - DataSource:
   - throttle (Boolean): Used to simulate a throttled stream (rate limited to `rate` per second)
   - rate: See above
   - numberOfSources: number of "telemetry sources" merged together
 - persistence
   - persistence (Boolean): Enable file persistence of raw data
   - filePath: path of the file
