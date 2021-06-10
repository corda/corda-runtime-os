gradlew clean appJar

run kafka broker with auto.create.topics.enable=false

java -jar components\examples\demo-app\build\bin\corda-demo-app-5.0.0-SNAPSHOT.jar {instanceId} {topicPrefix}
