to run
- run zookeeper, run kafka server at default port of 9092
- `gradlew appJar`
- for ease of passing in the files run the command from the resource folder

  `java -jar -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005  ..\..\..\build\bin\corda-demo-app-5.0.0-SNAPSHOT.jar --instanceId 1 --kafka kafka.
  properties --topic topic.conf --config config.conf`