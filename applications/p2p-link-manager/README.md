# P2p Link Manager Application
A standalone Link Manager application

## Building
To build run:
`./gradlew :applications:p2p-link-manager:clean :applications:p2p-link-manager:appJar`
This will create an executable jar in `applications/p2p-link-manager/build/bin/` 

## Running
Before starting the application, run a kafka cluster. See examples in [here](../../testing/message-patterns/README.md).
To run the application use:
`java -jar ./applications/p2p-link-manager/build/bin/corda-p2p-link-manager-5.0.0.0-SNAPSHOT.jar`

### Command arguments:
```
      --config-topic-name=<configTopicName>
               The config topic name (default: ConfigTopic)
  -h, --help   Display help and exit
  -i, --instance-id=<instanceId>
               The unique instance ID (default to random number)
  -k, --kafka-servers=<kafkaServers>
               The kafka servers (default: localhost:9092)
      --topic-prefix=<topicPrefix>
               The topic prefix (default: )
```

## Docker image
### Building the docker image
To build a docker image, run:
```bash
./gradlew :applications:p2p-link-manager:publishOSGiImage
```

### Running a container
You can configure the image using those environment variables:
* `KAFKA_SERVERS` - The list of Kafka server (default to `localhost:9092`)
* `CONFIG_TOPIC` - The  name of the configuration topic (default to `ConfigTopic`)
* `TOPIC_PREFIX` - The topic prefix (default to empty string)
* `INSTANCE_ID` - The Link Manager instance ID (default to random number)

### Example
1. Before starting the application, run a kafka cluster. See examples in [here](../../testing/message-patterns/README.md).
2. Build the docker image (see above)
3. Run the docker image:
```bash
docker run \
--rm \
--network kafka-docker_default \
-e KAFKA_SERVERS="broker1:9093" \
corda-os-docker-dev.software.r3.com/corda-os-p2p-link-manager:5.0.0.0-SNAPSHOT
```
Please note:
* The image need to be able to talk with the kafka broker, hence the network and `KAFKA_SERVERS` environment variable.
* The configuration host name must be the same as the docker host name.
