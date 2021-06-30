The purpose of this application is to read all the configuration available on a given kafka topic

The application runs via executable java jar with some added parameters
e.g. `java -jar corda-kafka-reader-5.0.0.jar --config bootstrapConfigurationFile`

The `bootstrapConfigurationFile` should contain a JSON/HOCON representation of the configuration you want to use to 
bootstrap the read service
```text
corda {
    kafka {
        topic.name=default-topic
    }
}
```

How to run locally

- run zookeeper, run kafka server
- `gradlew appJar`

for ease of passing in the files run the command from the resource folder

- `java -jar ..\..\..\build\bin\corda-kafka-setup-5.0.0-SNAPSHOT.jar --config kafka.conf`