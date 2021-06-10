gradlew clean appJar

run kafka broker with auto.create.topics.enable=false

java -jar applications\examples\demo-app\build\bin\corda-demo-app-5.0.0-SNAPSHOT.jar --instanceId 1 --kafka kafka.properties --topic topic.conf --config config.conf
