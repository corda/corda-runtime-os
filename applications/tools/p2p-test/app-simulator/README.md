This is a tool that can be used to simulate the application layer by sending/receiving messages to/from the p2p layer.

It can be executed in two main modes:
* `SENDER`: in this mode, the tool sends messages to the Kafka topics monitored by p2p components and writes metadata to a postgres DB.
* `RECEIVER`: in this mode, the tool receives messages from the Kafka topics where p2p delivers them and writes metadata to a postgres DB.

## Building the tool

```
./gradlew applications:tools:p2p-test:app-simulator:clean
./gradlew applications:tools:p2p-test:app-simulator:appJar
```

## Executing the tool

```
java -jar applications/tools/p2p-test/app-simulator/build/bin/corda-app-simulator-5.0.0.0-SNAPSHOT.jar --kafka ~/Desktop/kafka.properties --simulator-config ~/Desktop/simulator.conf
```

The file specified in the --kafka CLI parameter should have the following structure:
```
bootstrap.servers=localhost:9092
```

The simulator configuration file differs depending on the mode.

### Sender mode

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
        batchSize: 10,
        interBatchDelay: 0ms,
        messageSizeBytes: 10000
    }
}
```

The `loadGenerationType` can have two values:
* `ONE_OFF`: this is a one-off generation of load. It will generate a specific number of messages (as specified by `totalNumberOfMessages`) and then stop.
* `CONTINUOUS`: this is a continuous generation of load. It will send messages in a closed loop (i.e. send messages, wait until they are delivered to Kafka and metadata written to DB, send next batch etc.) until the tool is stopped via Ctrl+C.

The following configuration options are optional:
* `messageSizeBytes`: this is the size of the payload for the generated messages (random data will be generated). Default: 10KB.
* `parallelClients`: this is the number of parallel clients/threads used to send messages. Default: 1. Note: each client will send `totalNumberOfMessages` individually.
* `interBatchDelay`: the delay introduced between each batch of messages. Default: no delay.
* `batchSize`: the number of messages sent in parallel on every batch. Default: 50.

In this mode, the tool will generate messages to the topic p2p will process them from (`p2p.out`). You can customise the topic messages will be sent to using the CLI argument `--send-topic`, e.g.:
```
java -jar applications/tools/p2p-test/app-simulator/build/bin/corda-app-simulator-5.0.0.0-SNAPSHOT.jar --kafka ~/Desktop/kafka.properties --simulator-config ~/Desktop/simulator.conf --send-topic my.topic
```

### Receiver mode

In the receiver mode, the configuration file should have the following form:
```
{
	dbParams: {
        username: "corda",
        password: "corda-p2p-masters",
        host: "localhost",
        db: "corda"
    },
    parallelClients: 1
    simulatorMode: "RECEIVER"
}
```

In this mode, the tool will run until explicitly stopped with Ctrl+C.

The following configuration options are optional:
* `parallelClients`: the number of parallel clients/threads consuming messages from Kafka. Default: 1.

In this mode, the tool will consume messages from the default topic where p2p delivers messages (`p2p.in`). You can customise the topic messages will be sent to using the CLI argument `--receive-topic`, e.g.:
```
java -jar applications/tools/p2p-test/app-simulator/build/bin/corda-app-simulator-5.0.0.0-SNAPSHOT.jar --kafka ~/Desktop/kafka.properties --simulator-config ~/Desktop/simulator.conf --receive-topic my.topic
```

## Database metadata

The tool writes some additional metadata to a postgres database that can be used for further analysis.

### Sender mode

In sender mode, the tool writes metadata to the table `sent_messages` with the following columns:
* `sender_id`: an identifier for the sender, which is unique for every run of the tool.
* `message_id`: the identifier of the message that was sent.
* `sent_time`: a timestamp corresponding to the time the message was sent.

### Receiver mode

In sender mode, the tool writes metadata to the table `received_messages` with the following columns:
* `sender_id`: the identifier of the sender of the received message.
* `message_id`: the identifier of the message that was received.
* `received_time`: a timestamp corresponding to the time the message was received.

### Metadata analysis

Below are some useful SQL statements to analyse the metadata.

#### Verifying reliable delivery

After your testing is complete, you can check all sent messages have been delivered using this query:
```
select sm.sender_id, sm.message_id
from sent_messages sm 
left join received_messages rm 
on sm.sender_id = rm.sender_id and sm.message_id = rm.message_id 
where rm.message_id is null
```

#### Calculating latencies & aggregate statistics

In order to calculate the latency of the delivery of every message (in ms), you can run the following query:
```
create table latencies
as
select sm.sender_id as sender_id, sm.message_id as message_id, sm.sent_time as sent_time, rm.received_time as received_time, extract(epoch from (rm.received_time - sm.sent_time)) * 1000 as latency
from sent_messages sm join received_messages rm 
on sm.sender_id = rm.sender_id and sm.message_id = rm.message_id 
```

After creating the table, you can create statistics for 30 seconds time windows using the following query (replace <sender-id> with the right value):
```
select 
	to_timestamp(floor((extract('epoch' from l.sent_time) / 30 )) * 30) at time zone 'utc' as time_window,
	count(l.latency) as total_messages,
	max(l.latency) as max_latency,
	min(l.latency) as min_latency,
	avg(l.latency) as average_latency,
	percentile_disc(0.99) within group (order by l.latency) as p99_latency
from latencies l 
where sender_id = '<your-sender-id>'
group by time_window
order by time_window asc
```

## Deploying a postgres database

This project contains a Docker compose file that can be used to start a Docker container with a postgres database containing the tables needed.

In order to deploy this locally, you can use the following command:
```
docker-compose -f src/test/resources/postgres-docker/postgres-db.yml up -d
```
