to run
- run zookeeper, run kafka server at default port of 9092
- `gradlew appJar`
  for ease of passing in the files run the command from the resource folder
- `java -jar ..\..\..\build\bin\corda-kafka-setup-5.0.0-SNAPSHOT.jar kafka.properties topic.conf config.conf`