The purpose of this application is to read all the configuration available on a given kafka topic

The application runs via executable java jar with some added parameters
e.g. `java -jar corda-kafka-reader-5.0.0.jar --kafka kafkaPropertiesFile --topic topicName`

The `kafkaPropertiesFile` will contain the properties kafka needs to connect to the broker, like

```properties 
bootstrap.servers=localhost:9092
kafka.subscription.consumer.poll.timeout=1000
kafka.subscription.consumer.thread.stop.timeout=30000
kafka.subscription.consumer.processor.retries=3
kafka.subscription.consumer.subscribe.retries=3
kafka.subscription.consumer.commit.retries=3
```

The `topicName` is just the string name of the topic



How to run locally

- run zookeeper, run kafka server
- `gradlew appJar`

for ease of passing in the files run the command from the resource folder

- `java -jar ..\..\..\build\bin\corda-kafka-setup-5.0.0-SNAPSHOT.jar kafka.properties topic.conf config.conf`