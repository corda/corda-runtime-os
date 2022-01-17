# P2p Gateway Application
A standalone Gateway application

## Building
To build run:
`./gradlew :applications:p2p-gateway:clean :applications:p2p-gateway:appJar`
This will create an executable jar in `applications/p2p-gateway/build/bin/` 

## Running
To run the application use:
`java -jar ./applications/p2p-gateway/build/bin/corda-p2p-gateway-5.0.0.0-SNAPSHOT.jar`

### Command arguments:
```
  -h, --help   Display help and exit
  -i, --instance-id=<instanceId>
               The unique instance ID (default to random number)
  -k, --kafka-servers=<kafkaServers>
               The kafka servers (default: localhost:9092)
      --topic-prefix=<topicPrefix>
               The topic prefix (default: )
```

## Example
1. Before starting the application, run a kafka cluster. See examples in [here](../../testing/message-patterns/README.md).
2. Start the app:
```bash
java \
  -Djdk.net.hosts.file=./components/gateway/src/integration-test/resources/hosts \
  -jar ./applications/p2p-gateway/build/bin/corda-p2p-gateway-5.0.0.0-SNAPSHOT.jar
```

The `-Djdk.net.hosts.file` will overwrite the hosts file, allow the JVM to open localhost as if it was `www.alice.net`

See [here](../tools/p2p-test/p2p-configuration-publisher/README.md) on how to publish the gateway configuration


## Docker image
### Building the docker image
To build a docker image, run:
```bash
./gradlew :applications:p2p-gateway:publishOSGiImage
```

### Running a container
You can configure the image using those environment variables:
* `KAFKA_SERVERS` - The list of Kafka server (default to `localhost:9092`)
* `TOPIC_PREFIX` - The topic prefix (default to empty string)
* `INSTANCE_ID` - The Gateway instance ID (default to random number)

### Example
1. Before starting the application, run a kafka cluster. See examples in [here](../../testing/message-patterns/README.md).
2. Build the docker image (see above)
3. Run the docker image:
```bash
docker run \
--rm \
--network kafka-docker_default \
-e KAFKA_SERVERS="broker1:9093" \
-p 3123:5603 \
--hostname www.alice.net \
corda-os-docker-dev.software.r3.com/corda-os-p2p-gateway:5.0.0.0-SNAPSHOT
```
Please note:
* The image need to be able to talk with the kafka broker, hence the network and `KAFKA_SERVERS` environment variable.
* The configuration host name must be the same as the docker host name.
