# Tracing

This module allows us to give each REST request a unique trace ID that is logged with every log message related to that request.
It also supports submitting the trace to a Zipkin server that collects data for presenting via a dashboard.

Tracing is made up of two IDs `traceId` and `spanId`.
- `traceId` is the unique identifier for the request and remains the same throughout all work for that request.
- `spanId` is nested within a `traceId` and represents a piece of work we want to measure, there can be multiple spans within a request

## Configuration

The system will include these two IDs inside the loging MDC allowing us to tie all the log messages to a request.

To feed data into a dashboard you need to set the `CORDA_TRACING_SERVER_ZIPKIN_PROTOCOL` environment variable.

```shell
CORDA_TRACING_SERVER_ZIPKIN_PROTOCOL=http://localhost:9411
```

## Providing IDs on REST requests

_This is optional, if you don't provide an ID, one will be generated for you._

The trace ID can be provided to the REST endpoint in HTTP headers.
Both headers must be provided otherwise the system will treat the ID as missing and generate a new ID.

- `X-B3-TraceId` - The trace ID as a 64 or 128bit binary number encoded in hexadecimal
- `X-B3-SpanId` - The span ID as a 64bit binary number encoded in hexadecimal

```shell
TRACE_ID=`openssl rand -hex 16` # 16 bytes, 128 bits
SPAN_ID=`openssl rand -hex 8`   #  8 bytes,  64 bits
curl --insecure -u admin:admin --header "X-B3-TraceId: $TRACE_ID" --header "X-B3-SpanId: $SPAN_ID"  https://localhost:8888/api/v5_3/flow/$HOLDING_ID/r1
```

## How to use

Here we describe how to run the combined worker with a Kafka message bus and a tracing dashboard.

1. Setup kafka
2. Create the `docker-compose.yml` file:
    ```yml
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
3. Start the Kafka cluster through Docker Compose:
    ```shell
    docker compose -p kafka-cluster up -d
    ```
4. Create the required topics by building and executing the `topic` plugin:
    ```bash
    ./gradlew :tools:corda-cli:clean :tools:corda-cli:build
    ./tools/corda-cli/build/cli/corda-cli.sh topic -b=localhost:9092 create connect
    ```
5. Open a terminal in `corda-runtime-os/metrics` and run `docker compose up`
6. Build combined worker with kafka support:
    ```shell
    ./gradlew :applications:workers:release:combined-worker:clean :applications:workers:release:combined-worker:appJar -PbusImpl=kafka
    ```
7. Start the combined worker with the `--send-trace-to` command line parameter
    ```bash
    java -jar -Dco.paralleluniverse.fibers.verifyInstrumentation=true                      \
      ./applications/workers/release/combined-worker/build/bin/corda-combined-worker-*.jar \
      --instance-id=0                                                                      \
      -mbus.busType=KAFKA                                                                  \
      -mbootstrap.servers=localhost:9092                                                   \
      -spassphrase=password                                                                \
      -ssalt=salt                                                                          \
      -ddatabase.user=user                                                                 \
      -ddatabase.pass=password                                                             \
      -ddatabase.jdbc.directory=applications/workers/release/combined-worker/drivers       \
      -ddatabase.jdbc.url=jdbc:postgresql://localhost:5432/cordacluster                    \
      --send-trace-to=http://localhost:9411
    ```
8. Visit http://localhost:3000/ to view the dashboard
