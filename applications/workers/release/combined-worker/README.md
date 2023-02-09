# Combined Worker

## Setup

All commands are assumed to be executed at the root of the `corda-runtime-os` project.

### Postgres DB

Run postgres container:
```shell
docker run --rm -p 5432:5432 --name postgresql -e POSTGRES_DB=cordacluster -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password postgres:latest
```

Note that the above will give you a new "clean" DB every time the container is started. If you want to run postgres in the background and maintain the state until you manually clean, then use:

```shell
docker run -d -p 5432:5432 --name postgresql -e POSTGRES_DB=cordacluster -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password postgres:latest
```

DB schema will be created automatically when worker is started.

**NOTES:**

* DB bootstrapping might change as CLI could be used instead, for example. Options are being looked at by the DevEx team.
* Currently, the bootstrapper expects a postgres connection with the a superuser with credentials `postgres`/`password` 
(as per docker command above). If you need to use different credentials, you can specify them with the following environment variables:
  * `CORDA_DEV_POSTGRES_USER`
  * `CORDA_DEV_POSTGRES_PASSWORD`


### Kafka Cluster (Optional)

By default, the combined-worker uses the DB Message Bus emulation. This means that Kafka is not required, instead the 
message API will be supported by the postgres DB created above. If a "real" Kafka message bus is preferred, for 
debugging or troubleshooting message library issues as an example, then a real local Kafka cluster is needed.

Create the `docker-compose.yml` file:
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

Start the Kafka cluster through Docker Compose:
```shell
docker compose -p kafka-cluster up -d
```

Create the required topics by building and executing the `topic` plugin: 
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

Or if you want to connect to "real" Kafka:
```bash
./gradlew :applications:workers:release:combined-worker:clean :applications:workers:release:combined-worker:appJar -PbusImpl=kafka
```

:bulb: there's currently [an issue](https://github.com/xerial/snappy-java/issues/302) with the OSGi metadata published 
by the `snappy-java` library (pulled as a transitive dependency by `org.apache.servicemix.bundles.kafka-clients`). As a 
temporal fix, the library must be excluded [here](../../../../libs/messaging/kafka-message-bus-impl/build.gradle): 
```groovy
implementation ("org.apache.servicemix.bundles:org.apache.servicemix.bundles.kafka-clients:$kafkaClientVersion") {
    exclude group: 'org.xerial.snappy'
}
```

Run the worker using:
```bash
java -jar -Dco.paralleluniverse.fibers.verifyInstrumentation=true \
  ./applications/workers/release/combined-worker/build/bin/corda-combined-worker-*.jar \
  --instance-id=0 -mbus.busType=DATABASE  \
  -spassphrase=password -ssalt=salt \
  -ddatabase.user=user -ddatabase.pass=password \
  -ddatabase.jdbc.directory=applications/workers/release/combined-worker/drivers \
  -ddatabase.jdbc.url=jdbc:postgresql://localhost:5432/cordacluster
```

Or if you want to connect to "real" Kafka:
```bash
java -jar -Dco.paralleluniverse.fibers.verifyInstrumentation=true \
  ./applications/workers/release/combined-worker/build/bin/corda-combined-worker-*.jar \
  --instance-id=0 -mbus.busType=KAFKA -mbootstrap.servers=localhost:9092 \
  -spassphrase=password -ssalt=salt \
  -ddatabase.user=user -ddatabase.pass=password \
  -ddatabase.jdbc.directory=applications/workers/release/combined-worker/drivers \
  -ddatabase.jdbc.url=jdbc:postgresql://localhost:5432/cordacluster
```

### From IntelliJ IDE

Run or debug the `Combined Worker Local` target. If you want to break into the debugger at startup, set a breakpoint
on the [CombinerWorker.kt startup method](src/main/kotlin/net/corda/applications/workers/combined/CombinedWorker.kt).

IntelliJ by default does not kill debugged processes as soon as you stop them, and in particular the combined worker
will often stay running for some time, still listening on its configured port, so you will not be able to start a new
instance. There is an option under *Preferences / Build, Execution & Deployment / Debugger / Java section / Kill the
debug process immediately*  that makes iterative debugging the combined work much easier.

## Interact with the worker

The worker will expose the HTTP API on port 8888: https://localhost:8888/api/v1/swagger
The status endpoint is also exposed: http://localhost:7000/status

## Smoketests

Run the [smoketests](/applications/workers/workers-smoketest/) to validate the combined worker.

Note that some tests require an empty environment (e.g. CPI upload).

## Logs

Logs are output to disk, using the `osgi-framework-bootstrap/src/main/resources/log4j2.xml` configuration.
Logging level for 3rd party libs has been defaulted to WARN to reduce the log size/increase the usefulness in normal running,
but it may be useful to change this on a case-by-case basis when debugging. Note that the JAR must be rebuilt
after the resource file is changed. Here is an `log4j2.xml` which logs to the console only:

```
?xml version="1.0" encoding="UTF-8"?>
<Configuration status="INFO">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
        </Console>
    </Appenders>
    <Loggers>
        <logger name="Console">
            <AppenderRef ref="Console" level="info"/>
        </logger>

        <!-- log warn only for these 3rd party libs -->
        <Logger name="org.apache.aries.spifly" level="warn" />
        <Logger name="org.apache.kafka" level="warn" />
        <Logger name="io.javalin.Javalin" level="warn" />
        <Logger name="org.eclipse.jetty" level="warn" />
        <Logger name="org.hibernate" level="warn" />

        <!-- default to warn only for OSGi logging -->
        <Logger name="net.corda.osgi.framework.OSGiFrameworkWrap" level="warn" />

        <root level="debug">
            <AppenderRef ref="Console" level="info"/>
        </root>
    </Loggers>
</Configuration>
```

## Metrics

Corda exposes Prometheus metrics.
In order to view this metrics, when running the combined worker, you can use the configuration in  `../../../../metrics`.
See [metrics](../../../../metrics/readme.md) for further documentation.

## Security Manager

Security Manager can get in the way when debugging the code that runs inside the sandbox. For example, evaluating an expression while debugging a flow might result with access denied error. 
To initialize Security Manager with all permissions enabled, start the worker with command line argument `-DsecurityPolicyAllPermissions=true`.
To disable Security Manager, start the worker with command line argument `-DsecurityMangerEnabled=false`.
Note that these arguments should be used only for development purposes.  