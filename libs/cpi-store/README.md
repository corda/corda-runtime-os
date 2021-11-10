# Configuration Requirements

## cpi-read-impl-file

**CPIDirectory** - Specifies which directory to start file watching from. Note that this directory and all subdirectories are watched. The file
pattern to watch is "*.{cpi,cpb}" which is currently hardcoded.

## cpi-read-impl-kafka

**CPIKafkaCacheFilePath** - When an CPI input stream is retrieved the corresponding file is stored in this path.

## cpi-write-impl-file

Because this implementation also makes use of the file read implementation. **CPIDirectory** also needs to be specified.

## Integration Tests

To run the integration tests first start a Kafka cluster, use the following command,

```console
docker-compose -f testing/message-patterns/kafka-docker/single-kafka-cluster.yml up
```

Then run the integration tests via the following command,

```console
./gradlew clean :libs:cpi-store:integration-test:kafkaIntegrationTest
```