# HTTP RPC Gateway Application

An entry point to demonstrate use of HTTP RPC functionality.

Build:
`gradlew clean appJar`

The artefact is available under: `applications/http-rpc-gateway/build/bin`

Run worker(s) from separate dirs. For example:

- From resources/node1: `java -jar corda-http-rpc-gateway-5.0.0.0-SNAPSHOT.jar --instanceId 1 --kafka kafka.properties`
- From resources/node2: `java -jar corda-http-rpc-gateway-5.0.0.0-SNAPSHOT.jar --instanceId 2 --kafka kafka.properties`

Command line args/system properties can be used instead of a kafka properties file
- `java -jar -Dbootstrap.servers=localhost:9092 -Dconfig.topic.name=ConfigTopic -Dmessaging.topic.prefix=http-rpc-gateway corda-demo-app-5.0.0-SNAPSHOT.jar --instanceId 4`

## running a docker image
The gradle task publishOSGiImage publishes a Docker Image which can be run locally. Once available the image can be run as follows

### Running the container
```
docker run -it -p 8888:8888 engineering-docker-dev.software.r3.com/corda-os-http-rpc-gateway:latest
```

### Debugging the container
To debug a running container we can use the JAVA_TOOL_OPTIONS environment variable to pass arguments at runtime e.g.

```
docker run -it -p 8887:8888 -p 5005:5005 -e "JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005" engineering-docker-dev.software.r3.com/corda-os-http-rpc-gateway:latest
```

For further details on the image creation process see [Jar containerization](../../buildSrc/README.md#Create Docker Image Custom Gradle Task)