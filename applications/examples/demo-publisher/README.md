gradlew clean appJar

run kafka broker(3)

java -jar applications\examples\demo-app\build\bin\corda-demo-app-5.0.0-SNAPSHOT.jar --instanceId 1 --kafka kafka.properties --topic topic.conf --config config.conf
