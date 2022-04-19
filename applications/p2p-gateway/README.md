# P2P Gateway worker
The p2p gateway worker.

## Building the worker

### Building the JAR

To build the JAR artefact, run:
```bash
./gradlew :applications:p2p-gateway:clean :applications:p2p-gateway:appJar
```
This will create an executable JAR in `applications/p2p-gateway/build/bin/`.

### Building the Docker image

To build the Docker image, run:
```bash
./gradlew :applications:p2p-gateway:publishOSGiImage
```

## Running the worker

### Running the JAR

To run the JAR, use:
```bash
java -jar ./applications/p2p-gateway/build/bin/corda-p2p-gateway*.jar
```

Below is a list of command line arguments you can use:
```bash
  -h, --help   Display help and exit
  -i, --instance-id=<instanceId>
               The unique instance ID (default to random number)
  -k, --kafka-servers=<kafkaServers>
               A comma-separated list of addresses of Kafka brokers (default: localhost:9092)
      --topic-prefix=<topicPrefix>
               The topic prefix (default: )
```

### Running the Docker image

To run the Docker image, run:
```bash
docker run \
-e KAFKA_SERVERS="broker1:9093" \
-p 8085:8085 \
--hostname www.alice.net \
corda-os-docker-dev.software.r3.com/corda-os-p2p-gateway:<version>
```
Note that:
* you might need to expose the port the gateway is configured to listen to for connections.
* you might need to have the container host name match the DNS name the gateway's TLS certificate is bound to.

Below is a list of environment variables you can use:
* `KAFKA_SERVERS` - The list of Kafka server (default to `localhost:9092`)
* `TOPIC_PREFIX` - The topic prefix (default to empty string)
* `INSTANCE_ID` - The Gateway instance ID (default to random number)