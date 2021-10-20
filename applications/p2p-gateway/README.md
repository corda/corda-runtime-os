# P2p Gateway Application
A standalone Gateway application

## Building
To build run:
`./gradlew :applications:p2p-gateway:clean :applications:p2p-gateway:appJar`
This will create an executable jar in `applications/p2p-gateway/build/bin/` 

## Running
To run the application use:
`java -jar ./applications/p2p-gateway/build/bin/corda-p2p-gateway-5.0.0.0-SNAPSHOT.jar`

### Command arguments:
```
      --config-topic-name=<configTopicName>
                          The config topic name (default: ConfigTopic)
  -h, --help              Display help and exit
      --host=<hostname>   The name of the HTTP host (default: localhost)
  -i, --instance-id=<instanceId>
                          The unique instance ID (default to random number)
  -k, --kafka-servers=<kafkaServers>
                          The kafka servers (default: localhost:9092)
      --keyStore=<keyStoreFile>
                          The key store file (default: keystore.jks)
      --keyStorePassword=<keyStorePassword>
                          The key store password (default: password)
      --port=<port>       The HTTP port (default: 80)
      --revocationCheck=<revocationCheck>
                          Revocation Check mode (one of: SOFT_FAIL, HARD_FAIL,
                            OFF)
      --topic-prefix=<topicPrefix>
                          The topic prefix (default: gateway)
      --trustStore=<trustStoreFile>
                          The trust store file (default: truststore.jks)
      --trustStorePassword=<trustStorePassword>
                          The trust store password (default: password)
```
## TODO:
When https://r3-cev.atlassian.net/browse/CORE-2820 is ready we need to remove the configuration writer and the configuration arguments:
* `keyStore`
* `keyStorePassword`
* `port`
* `host`
* `revocationCheck`
* `trustStore`
* `trustStorePassword`

## Example
1. Before starting the application, run a kafka cluster. See examples in [here](../../libs/messaging/kafka-messaging-impl/src/kafka-integration-test/README.md).
2. Start the app:
```bash
java \
  -Djdk.net.hosts.file=./components/gateway/src/integration-test/resources/hosts \
  -jar ./applications/p2p-gateway/build/bin/corda-p2p-gateway-5.0.0.0-SNAPSHOT.jar \
  --keyStore ./components/gateway/src/integration-test/resources/sslkeystore_alice.jks \
  --trustStore ./components/gateway/src/integration-test/resources/truststore.jks \
  --port 3123 \
  --host www.alice.net
```

The `-Djdk.net.hosts.file` will overwrite the hosts file, allow the JVM to open localhost as if it was `www.alice.net`
The `keyStore` and `trustStore` are valid stores used in the integration tests.
