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
      --config-topic-name=<configTopicName>
               The config topic name (default: ConfigTopic)
  -h, --help   Display help and exit
  -k, --kafka-servers=<kafkaServers>
               The kafka servers (default: localhost:9092)
      --topic-prefix=<topicPrefix>
               The topic prefix (default: )
```
#### Gateway Arguments:
```
      --acquireTimeoutSec=<acquireTimeoutSec>
                          The client connection acquire timeout in seconds
                            (default: 10)
      --connectionIdleTimeoutSec=<connectionIdleTimeoutSec>
                          The amount of time to keep inactive client connection
                            before closing it in seconds (default: 60)
  -h, --help              display this help message
      --host=<hostname>   The name of the HTTP host (default: localhost)
      --keyStore=<keyStoreFile>
                          The key store file (default: keystore.jks)
      --keyStorePassword=<keyStorePassword>
                          The key store password (default: password)
      --maxClientConnections=<maxClientConnections>
                          The maximal number of client connections (default:
                            100)
      --port=<port>       The HTTP port (default: 80)
      --responseTimeoutMilliSecs=<responseTimeoutMilliSecs>
                          Time after which a message delivery is considered
                            failed in milliseconds (default: 1000)
      --retryDelayMilliSecs=<retryDelayMilliSecs>
                          Time after which a message is retried, when
                            previously failed in milliseconds (default: 1000)
      --revocationCheck=<revocationCheck>
                          Revocation Check mode (one of: SOFT_FAIL, HARD_FAIL,
                            OFF)
      --trustStore=<trustStoreFile>
                          The trust store file (default: truststore.jks)
      --trustStorePassword=<trustStorePassword>
                          The trust store password (default: password)
```
#### Link manager Arguments:
```
  -h, --help   display this help message
      --heartbeatMessagePeriodMilliSecs=<heartbeatMessagePeriodMilliSecs>
               Heartbeat message period in milli seconds (default: 2000)
      --locallyHostedIdentity=<locallyHostedIdentity>
               Local hosted identity (in the form of <x500Name>:<groupId>)
      --maxMessageSize=<maxMessageSize>
               The maximal message size (default: 500)
      --messageReplayPeriodSecs=<messageReplayPeriodSecs>
               message replay period in seconds (default: 2)
      --protocolMode=<protocolModes>
               Supported protocol mode (out of: AUTHENTICATION_ONLY,
                 AUTHENTICATED_ENCRYPTION; default: [AUTHENTICATED_ENCRYPTION])
      --sessionTimeoutMilliSecs=<sessionTimeoutMilliSecs>
               Session timeout in milliseconds (default: 10000)
```

#### File command:
To publish a configuration file, use the file command.
```bash
java -jar \
   ./applications/tools/p2p-test/configuration-publisher/build/bin/corda-configuration-publisher-5.0.0.0-SNAPSHOT.jar \
   file \
   <type> \
   <file>
```
Where type is either `gateway` or `linkmanager` and file is a valid Java properties, JSON or [conf](https://github.com/lightbend/config/blob/master/HOCON.md) file.

See example file in [here](example-gateway.conf).

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
--trustStore ./components/gateway/src/integration-test/resources/truststore.jks \
--port 3123 \
--host www.alice.net
```
The `keyStore` and `trustStore` are valid stores used in the integration tests.

Or
```bash
java \
-jar ./applications/tools/p2p-test/configuration-publisher/build/bin/corda-configuration-publisher-5.0.0.0-SNAPSHOT.jar \
file \
gateway \
./applications/tools/p2p-test/configuration-publisher/example-gateway.conf
```
