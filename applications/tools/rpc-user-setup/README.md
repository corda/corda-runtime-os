# RPC User Setup tool

This tool represents a standalone application which can be used to set up an RPC user with super admin privileges.

This tool is not a service/worker, i.e. once it is finished with its task it terminates.

## Running the tool

This tool is executed as part of [Docker Compose script](../../workers/release/deploy/docker-compose.yaml).

However, it is also possible to run it against existing Kafka broker of a composite node as a standalone application.

To build it:
```
gradlew :applications:tools:rpc-user-setup:appJar
```

To run it:
```
cd applications\tools\rpc-user-setup\build\bin
java -jar corda-rpc-user-setup-5.0.0.0-SNAPSHOT.jar --messagingParams kafka.common.bootstrap.servers=localhost:9093 --login admin --password admin
```