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
   --kafka-servers broker1:9093 \
   --topic p2p.out.markers \
   --values net.corda.p2p.markers.AppMessageMarker \
   --output ./p2p-deployment/reports/markers.txt
```

The command parameters are:
* `kafka-servers` A list of kafka servers
* `topic` The name of the topic to dump
* `values` The class name of the expected value
* `output` An output file to save all the data
