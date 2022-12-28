# P2P dump topic application

This is an application that can be used to dump the content of a topic into a file.

## Building the topic dumper

### Building the JAR

To build the JAR artefact, run:
```
./gradlew applications:tools:p2p-test:dump-topic:clean applications:tools:p2p-test:dump-topic:appJar
```
This will create an executable JAR in `applications/tools/p2p-test/dump-topic/build/bin/`.

## Running the app-simulator

### Running the JAR
To run the JAR, use:
```
java -jar \
   applications/tools/p2p-test/dump-topic/build/bin/corda-dump-topic*.jar \
   -mbootstrap.servers=broker1:9093 \
   --topic p2p.out.markers \
   --values net.corda.p2p.markers.AppMessageMarker \
   --output ./p2p-deployment/reports/markers.txt
```

The command parameters are: 
```
-m, --messaging-params=<String=String>
                        Messaging parameters for the topic dumper.
                          Default: {}
-o, --output=<output>   The output file.
-t, --topic=<topic>     The name of the topic to dump.
-v, --values=<values>   The class name of the expected value (full canonical
                          name).
```
By default, the topic dumper will try and connect to a Kafka broker on localhost:9092.
To override this use option `-m`. For example, to connect to a Kafka Broker on `kafka-broker:1000`:
```bash
java -jar applications/tools/p2p-test/app-simulator/build/bin/corda-app-simulator*.jar -mbootstrap.servers=kafka-broker:1000
```
These -m options are passed into the Kafka client. For example to use TLS to connect to the Kafka broker the following -m options can be used:
```bash
java -jar ./applications/p2p-link-manager/build/bin/corda-p2p-link-manager*.jar -msecurity.protocol=SSL -mssl.truststore.location=/certs/ca.crt -mssl.truststore.type=PEM
```

