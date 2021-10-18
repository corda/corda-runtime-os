build 
`gradlew clean appJar`

run from resources dir
`java -jar corda-demo-app-5.0.0-SNAPSHOT.jar --kafka kafka.properties --instanceId 1 --numberOfRecords 10 --numberOfKeys 4`

or run using command line args/system properties instead kafka.properties file

`java -jar -Dbootstrap.servers=localhost:9092 -Dmessaging.topic.prefix=demo corda-demo-publisher-5.0.0-SNAPSHOT.jar --instanceId 1 --numberOfRecords 10 --numberOfKeys 4`

for async publisher remove instanceId
`java -jar -Dbootstrap.servers=localhost:9092 -Dmessaging.topic.prefix=demo corda-demo-publisher-5.0.0-SNAPSHOT.jar --numberOfRecords 10 --numberOfKeys 4`
