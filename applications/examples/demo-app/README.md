gradlew clean appJar

run worker(s) from separate dirs. For example:

- From resources/node1: java -jar ..\..\..\..\build\bin\corda-demo-app-5.0.0-SNAPSHOT.jar --instanceId 1 --kafka ..\kafka.properties
- From resources/node2: java -jar ..\..\..\..\build\bin\corda-demo-app-5.0.0-SNAPSHOT.jar --instanceId 2 --kafka ..\kafka.properties
- From resources/node3: java -jar ..\..\..\..\build\bin\corda-demo-app-5.0.0-SNAPSHOT.jar --instanceId 3 --kafka ..\kafka.properties

