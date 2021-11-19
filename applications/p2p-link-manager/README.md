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
