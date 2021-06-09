The purpose of this application is to help set up the kafka topic required for configuration storage as well as pushing
configuration objects onto the newly created topic.

The application runs via executable java jar with some added parameters
e.g. `java -jar corda-kafka-setup-5.0.0.jar --kafka kafkaPropertiesFile --topic topicTemplateFile --config typesafeConfigurationFile`

The `kafkaPropertiesFile` will contain the properties kafka needs to connect to the broker, like

```properties 
bootstrap.servers=localhost:9092
```

The `topicTemplateFile` contains the typesafe definition for the topic you wish to create. For example

```text
topicName = "topicName"
numPartitions = 1
replicationFactor = 1
config {
    cleanup.policy=compact
}
```

The `typesafeConfigurationFile` should contain a JSON/HOCON representation of the configuration you want to save on the
topic that was created from the `topicTemplateFile`

```text
corda {
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = corda
        runMigration=true
        version="5.4.1"
    }
    version="5.4"
}
```

In this particular example, `corda` is the package name and `database` is the component name. These will be merged
together to form the key for the kafka record `corda.database` that has these contents

```properties
transactionIsolationLevel=READ_COMMITTED
schema=corda
runMigration=true
version="5.4.1"
```

How to run locally

- run zookeeper, run kafka server
- `gradlew appJar`

for ease of passing in the files run the command from the resource folder

- `java -jar ..\..\..\build\bin\corda-kafka-setup-5.0.0-SNAPSHOT.jar kafka.properties topic.conf config.conf`