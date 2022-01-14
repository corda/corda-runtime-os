Build:
`../../../gradlew clean appJar`

Resulting JAR file is: `build/bin/corda-demo-app-5.0.0.0-SNAPSHOT.jar`

Run worker(s) from separate dirs. For example:

- From resources/node1: `java -jar corda-demo-app-5.0.0.0-SNAPSHOT.jar --instanceId 1 --kafka kafka.properties`
- From resources/node2: `java -jar corda-demo-app-5.0.0.0-SNAPSHOT.jar --instanceId 2 --kafka kafka.properties`
- From resources/node3: `java -jar corda-demo-app-5.0.0.0-SNAPSHOT.jar --instanceId 3 --kafka kafka.properties`

Command line args/system properties can be used instead of a Kafka properties file
```
java -jar -Dbootstrap.servers=localhost:9092 -Dconfig.topic.name=config.topic \
    -Dmessaging.topic.prefix=demo build/bin/corda-demo-app-5.0.0.0-SNAPSHOT.jar --instanceId 4
```

Additional info on the demo process: https://github.com/corda/platform-eng-design/blob/master/core/corda-5/corda-5.1/flow-worker/message-patterns-demo.md