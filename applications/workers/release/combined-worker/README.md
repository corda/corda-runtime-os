# Combined Worker

## Setup

### Postgres DB

Run postgres container in background:
```shell
docker run -d -p 5432:5432 --name postgresql -e POSTGRESQL_DATABASE=cordacluster -e POSTGRESQL_USERNAME=user -e POSTGRESQL_PASSWORD=password -e POSTGRESQL_POSTGRES_PASSWORD=password bitnami/postgresql:latest
```

Run it with logs visible in console and ensure the container is deleted (and data wiped) when the container is stopped:

```shell
docker run --rm -p 5432:5432 --name postgresql -e POSTGRESQL_DATABASE=cordacluster -e POSTGRESQL_USERNAME=user -e POSTGRESQL_PASSWORD=password -e POSTGRESQL_POSTGRES_PASSWORD=password bitnami/postgresql:latest
```

DB schema will be created automatically when worker is started.
TODO: DB bootstraping might change as CLI could be used instead

### Message Bus

#### Kafka

TODO: Database message bus should be used instead, this is just temporary work-around (which is the reason why all containers are not created together).
Note that topics were taken from k8s cluster and might be out of sync.

Create file `docker-compose.yml`:
```
---
version: '2'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:latest
    container_name: kafka
    depends_on:
      - zookeeper
    ports:
      - 9092:9092
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_DEFAULT_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"

```
Run:
```shell
docker compose up -d
```

To create the topics:
Build the topic plugin, build the cli plugin host, copy the plugin and run it:
```bash
./gradlew :tools:plugins:topic-config:clean :tools:plugins:topic-config:cliPluginTask
cd ../corda-cli-plugin-host/
./gradlew assemble
cp ../corda-runtime-os/tools/plugins/topic-config/build/libs/topic-config-cli-plugin-*.jar ./build/plugins/
 ./build/generatedScripts/corda-cli.sh topic -b=localhost:9092 create connect
 cd ../corda-runtime-os/
```

## Start the worker
### From the command line
Build the worker using:
```bash
./gradlew :applications:workers:release:combined-worker:clean :applications:workers:release:combined-worker:appJar
```

Run the worker using:
```bash
java -jar \
  ./applications/workers/release/combined-worker/build/bin/corda-combined-worker-*.jar \
  --instanceId=0 -mbus.busType=KAFKA -mbootstrap.servers=localhost:9092 \
  -spassphrase=password -ssalt=salt -spassphrase=password -ssalt=salt \
  -ddatabase.user=user -ddatabase.pass=password \
  -ddatabase.jdbc.url=jdbc:postgresql://localhost:5432/cordacluster
```

### From IntelliJ IDE
From IntelliJ menu choose: `Run` -> `Run...` -> `Combined Worker Local-debug`

## Interact with the worker

The worker will expose the HTTP API on port 8888: https://localhost:8888/api/v1/swagger 
The status endpoint is also exposed: http://localhost:7000/status

## Smoketests

Run the [smoketests](/applications/workers/workers-smoketest/) to validate the combined worker.

Note that some tests require an empty environment (e.g. CPI upload).

## Logs

Logs are output to disk, using the `osgi-framework-bootstrap/src/main/resources/log4j2.xml` configuration.
Logging level for 3rd party libs has been defaulted to WARN to reduce the log size/increase the usefulness in normal running, 
but it may be useful to change this on a case-by-case basis when debugging.
