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
`docker run --name http-rpc-gw -d -p 8888:8888 http-rpc-gw`

Swagger UI can now be accessed using [local URL](https://localhost:8888/api/v1/swagger).

If you need to debug or indeed pass any Java option to a JVM that will be started inside container, there is an
environment variable for that: `JAVA_ARGS`.
To enable remote debugging container should be run as follows:

`docker run --name http-rpc-gw -d -p 8888:8888 -e JAVA_ARGS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -p 5005:5005 http-rpc-gw`

__NOTE:__ `-p 5005:5005` which forwards internal container debug port to a local port such that remote debugger could
be attached.