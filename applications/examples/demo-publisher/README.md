build 
`gradlew clean appJar`

run from resources dir
`java -jar ..\..\..\build\bin\corda-demo-app-5.0.0-SNAPSHOT.jar --kafka kafka.properties --instanceId 1`

or run using command line args/system properties instead kafka.properties file

`java -jar -Dbootstrap.servers=localhost:9092 -Dmessaging.topic.prefix=demo ..\..\..\build\bin\corda-demo-publisher-5.0.0-SNAPSHOT.jar --instanceId 1`

for async publisher remove instanceId
`java -jar -Dbootstrap.servers=localhost:9092 -Dmessaging.topic.prefix=demo ..\..\..\build\bin\corda-demo-publisher-5.0.0-SNAPSHOT.jar`
