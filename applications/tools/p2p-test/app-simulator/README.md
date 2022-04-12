# P2P application-simulator worker

This is an application that can be used to simulate the application layer by sending/receiving messages to/from the p2p layer and writing metadata for analysis to a relational database.

It can be executed in three main modes:
* `SENDER`: in this mode, the tool sends messages to the Kafka topics monitored by p2p components and (optionally) writes metadata to a postgres DB.
* `RECEIVER`: in this mode, the tool receives messages from the Kafka topics where p2p delivers them and writes them to a temporary topic along with metadata.
* `DB_SINK`: in this mode, the tool copies the metadata from Kafka into a relational database.

![Overview diagram](p2p_app_simulator.png)

## Building the application

To build run:
```
./gradlew applications:tools:p2p-test:app-simulator:clean applications:tools:p2p-test:app-simulator:appJar
```
This will create an executable jar in `applications/tools/p2p-test/app-simulator/build/bin/`.

## Executing the application

```
java -jar applications/tools/p2p-test/app-simulator/build/bin/corda-app-simulator-5.0.0.0-SNAPSHOT.jar --kafka-servers localhost:9092 --simulator-config ~/Desktop/simulator.conf
```

The simulator configuration file differs depending on the mode.

### Sender mode

In this mode, the tool will generate messages to the `p2p.out` topic, which is where the p2p layer processes them from by default. You can customise the topic messages will be sent to using the CLI argument `--send-topic`, e.g.:
```
java -jar applications/tools/p2p-test/app-simulator/build/bin/corda-app-simulator-5.0.0.0-SNAPSHOT.jar --kafka-servers localhost:9092 --simulator-config ~/Desktop/simulator.conf --send-topic my.topic
```
The tool will also (optionally) write some additional metadata (e.g. message ID, sender ID) to the specified database.

Whether you want to write metadata to the DB from the sender depends on the nature of testing:
* If you want to perform functional testing, it's useful to write this metadata to the DB so that you can check whether all sent messages were delivered.
* If you want to perform performance/stress testing, you can disable this part. This will prevent the DB from becoming a bottleneck and you will still get latency statistics for the delivered messages.

In the sender mode, the configuration file should have the following form:
```
{
    dbParams: {
        username: "corda",
        password: "corda-p2p-masters",
        host: "localhost",
        db: "corda"
    },
    parallelClients: 1,
    simulatorMode: "SENDER",
    loadGenerationParams: {
        peerX500Name: "O=Alice,L=London,C=GB",
        peerGroupId: "group-1",
        ourX500Name: "O=Bob,L=London,C=GB",
        ourGroupId: "group-1",
        loadGenerationType: "CONTINUOUS",  
        // totalNumberOfMessages: 1000 - only required when loadGenerationType = ONE_OFF
        batchSize: 10,
        interBatchDelay: 0ms,
        messageSizeBytes: 10000,
        expireAfterTime: 10
    }
}
```

The `loadGenerationType` can have two values:
* `ONE_OFF`: this is a one-off generation of load. It will generate a specific number of messages (as specified by `totalNumberOfMessages`) and then stop.
* `CONTINUOUS`: this is a continuous generation of load. It will send messages in a closed loop (i.e. send messages, wait until they are delivered to Kafka and metadata written to DB, send next batch etc.) until the tool is stopped via Ctrl+C.

The following configuration options are optional:
* `dbParams`: if this option is specified, the simulator will also write some metadata for each message (e.g. message ID, sender ID) to the specified database.
* `messageSizeBytes`: this is the size of the payload for the generated messages (random data will be generated). Default: 10KB.
* `parallelClients`: this is the number of parallel clients/threads used to send messages. Default: 1. Note: each client will send `totalNumberOfMessages` individually.
* `interBatchDelay`: the delay introduced between each batch of messages. Default: no delay.
* `batchSize`: the number of messages sent in parallel on every batch. Default: 50.
* `expireAfterTime`: the number of seconds for the Time To Live (TTL) of the messages. If left null the TTL of messages will never expire. Default: null.

### Receiver mode

In this mode, the tool will consume messages from the default topic where the p2p layer delivers messages (`p2p.in`). You can customise the topic messages will be sent to using the CLI argument `--receive-topic`, e.g.:
```
java -jar applications/tools/p2p-test/app-simulator/build/bin/corda-app-simulator-5.0.0.0-SNAPSHOT.jar --kafka-servers localhost:9092 --simulator-config ~/Desktop/simulator.conf --receive-topic my.topic
```
The consumed messages will be written to a secondary topic (`app.received_msg`) along with some additional metadata (e.g. timestamps, calculated latency).
In this mode, the tool will run until explicitly stopped with Ctrl+C.

In the receiver mode, the configuration file should have the following form:
```
{
    parallelClients: 1
    simulatorMode: "RECEIVER"
}
```

The following configuration options are optional:
* `parallelClients`: the number of parallel clients/threads consuming messages from Kafka. Default: 1.

### DB Sink mode

In this mode, the tool will copy all the metadata from the Kafka topic (`app.received_msg`) into the specified relational database for further analysis.

The configuration should have the following form:
```
{
	dbParams: {
        username: "corda",
        password: "corda-p2p-masters",
        host: "localhost",
        db: "corda"
    },
    parallelClients: 1
    simulatorMode: "DB_SINK"
}
```

The following configuration options are optional:
* `parallelClients`: the number of parallel clients/threads consuming messages from Kafka. Default: 1.

In this mode, the tool will run until explicitly stopped with Ctrl+C.

## Database metadata

The tool writes some additional metadata to a postgres database that can be used for further analysis.

### Sender mode

In this mode, when configured to write to a DB the tool writes metadata to the table `sent_messages` with the following columns:
* `sender_id`: an identifier for the sender, which is unique for every run of the tool.
* `message_id`: the identifier of the message that was sent.

### DB Sink mode

In this mode, the tool writes metadata to the table `received_messages` with the following columns:
* `sender_id`: the identifier of the sender of the received message.
* `message_id`: the identifier of the message that was received.
* `sent_timestamp`: a timestamp corresponding to the time the message was sent.
* `received_timestamp`: a timestamp corresponding to the time the message was received.
* `delivery_latency_ms`: the time the message took to be delivered end-to-end in milliseconds.

### Metadata analysis

Below are some useful SQL statements to analyse the metadata.

#### Verifying reliable delivery

After your testing is complete, you can check if there are any sent messages that weren't delivered using this query:
```
select sm.sender_id, sm.message_id
from sent_messages sm 
left join received_messages rm 
on sm.sender_id = rm.sender_id and sm.message_id = rm.message_id 
where rm.message_id is null
```

Note: in order to use this query, you must have configured the sender to produce data in the DB. Otherwise, it's going to be empty regardless.

#### Calculating latencies & aggregate statistics

In order to calculate the latency of the delivered messages (in ms), you can run the following query:
```
select 
	to_timestamp(floor((extract('epoch' from rm.sent_timestamp) / 30 )) * 30) at time zone 'utc' as time_window,
	count(rm.delivery_latency_ms) as total_messages,
	max(rm.delivery_latency_ms) as max_latency,
	min(rm.delivery_latency_ms) as min_latency,
	avg(rm.delivery_latency_ms) as average_latency,
	percentile_disc(0.99) within group (order by rm.delivery_latency_ms) as p99_latency
from received_messages rm 
group by time_window
order by time_window asc
```

If you want to calculate latencies only for a specific sender, you can add a `where sender_id = '<your-sender-id>'` clause (replace `<sender-id>` with the right value).

## Deploying a postgres database

This project contains a Docker compose file that can be used to start a Docker container with a postgres database containing the tables needed.

In order to deploy this locally, you can use the following command:
```
docker-compose -f src/test/resources/postgres-docker/postgres-db.yml up -d
```
In order to deploy this in the same network as the kafka cluster([see](../../../../testing/message-patterns/README.md)):
```
docker-compose -f applications/tools/p2p-test/app-simulator/src/test/resources/postgres-docker/postgres-db-with-kafka-network.yml up -d
```

## Using the tool's Docker image
### Building the image
To build a docker image of the tool run:
```bash
./gradlew :applications:tools:p2p-test:app-simulator:publishOSGiImage
```

The created image will be `corda-os-docker-dev.software.r3.com/corda-os-app-simulator:5.0.0.0-SNAPSHOT`

### Example of using the image
1. Start the kafka cluster([see](../../../../testing/message-patterns/README.md))
2. Add a database container to the network:
```bash
docker-compose \
  -f applications/tools/p2p-test/app-simulator/src/test/resources/postgres-docker/postgres-db-with-kafka-network.yml \
  up -d
```
3. Run the tool:
  * Sender with database:
```bash
docker run \
  -v $(pwd)/applications/tools/p2p-test/app-simulator/SenderConfigurationExample.conf:/config.conf \
  --rm \
  -e KAFKA_SERVERS="broker1:9093" \
 --network kafka-docker_default \
  corda-os-docker-dev.software.r3.com/corda-os-app-simulator:5.0.0.0-SNAPSHOT
```
  * Sender without database:
```bash
docker run \
  -v $(pwd)/applications/tools/p2p-test/app-simulator/SenderWithNoDbConfigurationExample.conf:/config.conf \
  --rm \
  -e KAFKA_SERVERS="broker1:9093" \
 --network kafka-docker_default \
  corda-os-docker-dev.software.r3.com/corda-os-app-simulator:5.0.0.0-SNAPSHOT
```
  * Receiver:
```bash
docker run \
  -v $(pwd)/applications/tools/p2p-test/app-simulator/ReceiverConfigurationExample.conf:/config.conf \
  --rm \
  -e KAFKA_SERVERS="broker1:9093" \
 --network kafka-docker_default \
  corda-os-docker-dev.software.r3.com/corda-os-app-simulator:5.0.0.0-SNAPSHOT
```
  * Sink:
```bash
docker run \
  -v $(pwd)/applications/tools/p2p-test/app-simulator/DbSinkConfigurationExample.conf:/config.conf \
  --rm \
  -e KAFKA_SERVERS="broker1:9093" \
 --network kafka-docker_default \
  corda-os-docker-dev.software.r3.com/corda-os-app-simulator:5.0.0.0-SNAPSHOT
```