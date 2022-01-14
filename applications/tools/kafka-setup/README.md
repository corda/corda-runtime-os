The purpose of this application is to help set up kafka topics required for configuration storage as well as pushing
configuration objects onto the newly created topic.

The application runs via executable java jar with some added property files
e.g. `java -jar corda-kafka-setup-5.0.0.jar --kafka kafkaPropertiesFile --topic topicTemplateFile --config typesafeConfigurationFile`
Command line args may also be used instead of kafkaPropertiesFile
e.g. `java -jar  -Dbootstrap.servers=localhost:9092 -Dconfig.topic.name=config.topic -Dmessaging.topic.prefix=demo build/bin/corda-kafka-setup-5.0.0.0-SNAPSHOT.jar --topic topics.conf --config config.conf`

The `kafkaPropertiesFile` will contain the properties kafka needs to connect to the broker, like

```properties 
bootstrap.servers=localhost:9092
config.topic.name=config.topic
messaging.topic.prefix=demo
```

The `topicTemplateFile` contains the typesafe definition for the topic you wish to create. For example

```text
topics = [
    {
        topicName = "config.topic"
        numPartitions = 1
        replicationFactor = 3
        config {
            cleanup.policy=compact
        }
    }
]
```
note: the prefix is not applied to the topic names defined in the topics file. 

The `typesafeConfigurationFile` should contain a JSON/HOCON representation of the configuration you want to save on the
topic that was created from the `topicTemplateFile`

```text
corda {
    messaging {
        componentVersion="5.1"
        subscription {
                consumer {
                    close.timeout = 6000
                    poll.timeout = 6000
                    thread.stop.timeout = 6000
                    processor.retries = 3
                    subscribe.retries = 3
                    commit.retries = 3
                }

                producer {
                    close.timeout = 6000
                }
            }
    }
    packageVersion="5.1"
}
```

In this particular example, `messaging` is the package name and `subscription` is the component name. These will be merged
together to form the key for the kafka record `messaging.subscription` that has these contents

```properties
consumer {
    close.timeout = 6000
    poll.timeout = 6000
    thread.stop.timeout = 6000
    processor.retries = 3
    subscribe.retries = 3
    commit.retries = 3
}

producer {
    close.timeout = 6000
}
```

How to run locally

- run zookeeper, run kafka server
- `gradlew appJar`

for ease of passing in the files run the command from the resource folder


To run topic creator:
- `java -jar corda-kafka-setup-5.0.0-SNAPSHOT.jar --kafka kafka.properties --topic topics.conf`

To send config to config topic:
- `java -jar corda-kafka-setup-5.0.0-SNAPSHOT.jar --kafka kafka.properties --config config.conf`

To do both in one run
- `java -jar corda-kafka-setup-5.0.0-SNAPSHOT.jar --kafka kafka.properties --topic topics.conf --config config.conf`
