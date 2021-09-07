Build:
`gradlew clean appJar`

The artefact is available under: `applications/http-rpc-gateway/build/bin`

Run worker(s) from separate dirs. For example:

- From resources/node1: `java -jar corda-http-rpc-gateway-5.0.0.0-SNAPSHOT.jar --instanceId 1 --kafka kafka.properties`
- From resources/node2: `java -jar corda-http-rpc-gateway-5.0.0.0-SNAPSHOT.jar --instanceId 2 --kafka kafka.properties`

Command line args/system properties can be used instead of a kafka properties file
-  `java -jar -Dbootstrap.servers=localhost:9092 -Dconfig.topic.name=ConfigTopic -Dmessaging.topic.prefix=http-rpc-gateway corda-demo-app-5.0.0-SNAPSHOT.jar --instanceId 4`