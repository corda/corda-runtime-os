# Kafka setup tool

The purpose of this tool is to help set up kafka topics required for configuration storage as well as pushing
configuration objects onto the newly created topic.

## Building the tool

To build the JAR artefact, run:
```
./gradlew :applications:tools:kafka-setup:clean :applications:tools:kafka-setup:appJar
```
This will create an executable JAR in `applications/tools/kafka-setup/build/bin`.

## Running the tool

### Creating topics
To run the tool to create Kafka topics, use:
```
java -jar applications/tools/kafka-setup/build/bin/corda-kafka-setup*.jar -Dbootstrap.servers=broker1:9093 --topic topics.conf
```

The `topics.conf` file must contain the definition for the topics you wish to create in the following form (in [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) format):
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
The `config` section can contain any configuration for topics in the notation specified by [Kafka](https://kafka.apache.org/documentation/#topicconfigs).

### Publishing configuration
To run the tool to publish configuration (in the topic `config.topic`), use:
```bash
java -jar applications/tools/kafka-setup/build/bin/corda-kafka-setup*.jar -Dbootstrap.servers=broker1:9093 -config config.conf
```

The `config.conf` file must contain the definition for the configuration in the following form:
```text
corda {
    messaging {
        componentVersion="5.1"
        subscription {
                consumer {
                    close.timeout = 6000
                    poll.timeout = 500
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
together to form the key for the kafka record `messaging.subscription` that has these contents:

```
consumer {
    close.timeout = 6000
    poll.timeout = 500
    thread.stop.timeout = 6000
    processor.retries = 3
    subscribe.retries = 3
    commit.retries = 3
}

producer {
    close.timeout = 6000
}
```

### Specifying Kafka connection details via a file

Instead of specifying the Kafka connection details via the `-Dbootstrap.servers` property, you can specify a file via the command line parameter `--kafka`.
This file must contain the connection details for Kafka as a comma-separated list of addresses of the Kafka brokers in the following form:
```properties 
bootstrap.servers=broker1:9093
```