# P2P Configuration publisher application
A utility to publish configuration for the p2p components

## Building
To build run:
`./gradlew :applications:tools:p2p-test:configuration-publisher:clean :applications:tools:p2p-test:configuration-publisher:appJar`
This will create an executable jar in `applications/p2p-gateway-config-publisher/build/bin` 

## Running
To run the application use:
`java -jar ./applications/tools/p2p-test/configuration-publisher/build/bin/corda-configuration-publisher-5.0.0.0-SNAPSHOT.jar <gateway/linkmanager/file>`

### Command arguments:
#### Common arguments:
```
      [@<filename>...]   One or more argument files containing options.
      --config-topic-name=<configTopicName>
                         The config topic name
                           Default: ConfigTopic
  -h, --help             Display help and exit
  -k, --kafka-servers=<kafkaServers>
                         The kafka servers
                           Default: localhost:9092
      --topic-prefix=<topicPrefix>
                         The topic prefix
                           Default:
```
#### Gateway Arguments:
```
      [@<filename>...]    One or more argument files containing options.
      --acquireTimeoutSec=<acquireTimeoutSec>
                          The client connection acquire timeout in seconds
                            Default: 10
      --connectionIdleTimeoutSec=<connectionIdleTimeoutSec>
                          The amount of time to keep inactive client connection
                            before closing it in seconds
                            Default: 60
  -h, --help              display this help message
      --hostAddress=<hostAddress>   The host name or IP address where the HTTP server will
                            bind
                            Default: 0.0.0.0
      --keyStore=<keyStoreFile>
                          The path to the key store file
      --keyStorePassword=<keyStorePassword>
                          The key store password
      --maxClientConnections=<maxClientConnections>
                          The maximal number of client connections
                            Default: 100
      --port=<port>       The HTTP port
      --responseTimeoutMilliSecs=<responseTimeoutMilliSecs>
                          Time after which a message delivery is considered
                            failed in milliseconds
                            Default: 1000
      --retryDelayMilliSecs=<retryDelayMilliSecs>
                          Time after which a message is retried, when
                            previously failed in milliseconds
                            Default: 1000
      --revocationCheck=<revocationCheck>
                          Revocation Check mode (one of: SOFT_FAIL, HARD_FAIL,
                            OFF)
                            Default: OFF
      --trustStore=<trustStoreFile>
                          The path to the trust store file
      --trustStorePassword=<trustStorePassword>
                          The trust store password
```
#### Link manager Arguments:
```
      [@<filename>...]   One or more argument files containing options.
  -h, --help             display this help message
      --heartbeatMessagePeriodMilliSecs=<heartbeatMessagePeriodMilliSecs>
                         Heartbeat message period in milli seconds
                           Default: 2000
      --locallyHostedIdentity=<locallyHostedIdentity>
                         Local hosted identity (in the form of <x500Name>:
                           <groupId>)
      --maxMessageSize=<maxMessageSize>
                         The maximal message size in bytes
                           Default: 1_000_000
      --messageReplayPeriodMilliSecs=<messageReplayPeriodMilliSecs>
                         message replay period in milliseconds
                           Default: 2000
      --protocolMode=<protocolModes>
                         Supported protocol mode (out of: AUTHENTICATION_ONLY,
                           AUTHENTICATED_ENCRYPTION)
                           Default: [AUTHENTICATED_ENCRYPTION]
      --sessionTimeoutMilliSecs=<sessionTimeoutMilliSecs>
                         Session timeout in milliseconds
                           Default: 10000
```

## Example
1. Before starting the application, run a kafka cluster. See examples in [here](../../../../testing/message-patterns/README.md).
2. Start the gateway: 
```bash
java \
  -Djdk.net.hosts.file=./components/gateway/src/integration-test/resources/hosts \
  -jar ./applications/p2p-gateway/build/bin/corda-p2p-gateway-5.0.0.0-SNAPSHOT.jar
```
The `-Djdk.net.hosts.file` will overwrite the hosts file, allow the JVM to open localhost as if it was `www.alice.net`
3. Publish the configuration:
```bash
java \
-jar ./applications/tools/p2p-test/configuration-publisher/build/bin/corda-configuration-publisher-5.0.0.0-SNAPSHOT.jar \
gateway \
--keyStore ./components/gateway/src/integration-test/resources/sslkeystore_alice.jks \
--keyStorePassword password \
--trustStore ./components/gateway/src/integration-test/resources/truststore.jks \
--trustStorePassword password \
--port 3123
```
The `keyStore` and `trustStore` are valid stores used in the integration tests.

Or, one can load the configuration from an arguments file. For example, from [gateway-args-example](gateway-args-example.txt):
```bash
java \
-jar ./applications/tools/p2p-test/configuration-publisher/build/bin/corda-configuration-publisher-5.0.0.0-SNAPSHOT.jar \
@./applications/tools/p2p-test/configuration-publisher/gateway-args-example.txt
```

## Docker image
### Building the docker image
To build a docker image, run:
```bash
./gradlew :applications:tools:p2p-test:configuration-publisher:publishOSGiImage
```

### Running a container
You can configure the image using those environment variables:
* `KAFKA_SERVERS` - The list of Kafka server
* `CONFIG_TOPIC` - The  name of the configuration topic (default to `ConfigTopic`)
* `TOPIC_PREFIX` - The topic prefix (default to empty string)

You can pass the key store, the trust store and the argument file using docker `--volume` or `--mount`.

### Example Using a file
1. Before starting the application, run a kafka cluster. See examples in [here](../../../../testing/message-patterns/README.md).
2. Build the docker image (see above)
3. Run the docker image:
```bash
docker run \
 --rm \
 -e KAFKA_SERVERS="broker1:9093" \
 --network kafka-docker_default \
 -v "$(pwd)/applications/tools/p2p-test/configuration-publisher/docker-args-example.txt:/args.txt" \
 -v "$(pwd)/components/gateway/src/integration-test/resources/sslkeystore_alice.jks:/keystore.jks" \
 -v "$(pwd)/components/gateway/src/integration-test/resources/truststore.jks:/truststore.jks" \
 corda-os-docker-dev.software.r3.com/corda-os-configuration-publisher:5.0.0.0-SNAPSHOT \
 @/args.txt
```
Please note:
* The image need to be able to talk with the kafka broker, hence the network and `KAFKA_SERVERS` environment variable.
* The argument file we are using is [this one](docker-args-example.txt).
*  Since the keystore and truststore are getting mounted to the correct name, there is no need to add them to the arguments.

### Example without a file
1. Before starting the application, run a kafka cluster. See examples in [here](../../../../testing/message-patterns/README.md).
2. Build the docker image (see above)
3. Run the docker image:
```bash
docker run \
 --rm \
 -e KAFKA_SERVERS="broker1:9093" \
 --network kafka-docker_default \
 -v "$(pwd)/components/gateway/src/integration-test/resources/sslkeystore_alice.jks:/keystore.jks" \
 -v "$(pwd)/components/gateway/src/integration-test/resources/truststore.jks:/truststore.jks" \
 corda-os-docker-dev.software.r3.com/corda-os-configuration-publisher:5.0.0.0-SNAPSHOT \
 gateway \
 --keyStore /keystore.jks \
 --keyStorePassword password \
 --trustStore /truststore.jks \
 --trustStorePassword password \
 --port 24123
```
Please note:
* The image need to be able to talk with the kafka broker, hence the network and `KAFKA_SERVERS` environment variable.
*  Since the keystore and truststore are getting mounted to the correct name, there is no need to add them to the arguments.
