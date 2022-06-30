# P2P Link Manager worker
The p2p link manager worker.

## Building the worker

### Building the JAR

To build the JAR artefact, run:
```
./gradlew :applications:p2p-link-manager:clean :applications:p2p-link-manager:appJar
```
This will create an executable JAR in `applications/p2p-link-manager/build/bin/`. 

### Building the Docker image

To build the Docker image, run:
```bash
./gradlew :applications:p2p-link-manager:publishOSGiImage
```

## Running the worker

### Running the JAR

To run the JAR, use:
```bash
java -jar ./applications/p2p-link-manager/build/bin/corda-p2p-link-manager*.jar
```

Below is a list of command line arguments you can use:
```
  -h, --help   Display help and exit
  -i, --instance-id=<instanceId>
               The unique instance ID (default to random number)
  -m, --messagingParams=<String=String>
               Messaging parameters for the link manager.
      --topicPrefix=<topicPrefix>
               The topic prefix (default: )
```
By default, the link-manager will try and connect to a Kafka broker on localhost:9092.
To override this use option `-m`. For example, to connect to a Kafka Broker on `kafka-broker:1000`:
```bash
java -jar ./applications/p2p-link-manager/build/bin/corda-p2p-link-manager*.jar -mbootstrap.servers=kafka-broker:1000
```
These -m options are passed into the Kafka client. For example to use TLS to connect to the Kafka broker the following -m options can be used:
```bash
java -jar ./applications/p2p-link-manager/build/bin/corda-p2p-link-manager*.jar -msecurity.protocol=SSL -mssl.truststore.location=/certs/ca.crt -mssl.truststore.type=PEM
```

### Running the Docker image

To run the Docker image, run:
```bash
docker run \
-e KAFKA_SERVERS="broker1:9093" \
corda-os-docker-dev.software.r3.com/corda-os-p2p-link-manager:<version>
```

Below is a list of environment variables you can use:
* `KAFKA_SERVERS` - The list of Kafka server (default to `localhost:9092`)
* `TOPIC_PREFIX` - The topic prefix (default to empty string)
* `INSTANCE_ID` - The Link Manager instance ID (default to random number)