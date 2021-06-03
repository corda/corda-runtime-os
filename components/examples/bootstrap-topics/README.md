to run
- run zookeeper, run kafka server at default port of 9092
- `gradlew appJar`
- `java -jar components\examples\bootstrap-topics\build\bin\corda-bootstrap-topics-5.0.0-SNAPSHOT.jar bootstrap.servers=localhost:9092 test12 config1.conf`