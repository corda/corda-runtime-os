# HTTP RPC Gateway Application

An entry point to demonstrate use of HTTP RPC functionality.

Build:\
`gradlew clean appJar`

The artefact is available under: `applications/http-rpc-gateway/build/bin`

Run worker(s) from separate dirs. For example:

- From resources/node1: `java -jar corda-http-rpc-gateway-5.0.0.0-SNAPSHOT.jar --instanceId 1 --kafka kafka.properties`
- From resources/node2: `java -jar corda-http-rpc-gateway-5.0.0.0-SNAPSHOT.jar --instanceId 2 --kafka kafka.properties`

Command line args/system properties can be used instead of a kafka properties file:\
`java -jar -Dbootstrap.servers=localhost:9092 -Dconfig.topic.name=ConfigTopic -Dmessaging.topic.prefix=http-rpc-gateway corda-demo-app-5.0.0-SNAPSHOT.jar --instanceId 4`

## Docker execution of the HTTP RPC Gateway

Build Docker Image (should be done after `gradlew clean appJar`):\
`docker build -t http-rpc-gw .`

Running  Docker container:\
`docker run --name http-rpc-gw -d -P http-rpc-gw`

A random vacant local port will be mapped on port `8888` exposed within container. To establish which port is that run:\
`docker ps -f name=http-rpc-gw`