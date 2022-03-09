This is a tool that can be used to act as a client worker which uses the `MembershipGroupReaderProvider` to read member 
lists from kafka and present a specific member's view on data. This is used mainly to verify member views on data and is not intended as a long term solution but rather a temporary tool until we have better methods of verification. The data printed to the console is a subset of member data and not intended to be a verbose list.

## Building the tool
```
./gradlew :applications:tools:mgm-test:memberlist-viewer:clean
./gradlew :applications:tools:mgm-test:memberlist-viewer:appJar
```

## Running the tool

```
java -jar applications/tools/mgm-test/memberlist-viewer/build/bin/corda-memberlist-viewer-5.0.0.0-SNAPSHOT.jar \
    --kafka ~/Desktop/kafka.properties --group ABC-123 \
    --member "C=GB, L=London, O=Alice" --member "C=GB, L=London, O=Bob"  --member "C=GB, L=London, O=Charlie"
```

The file specified in the `--kafka` CLI parameter should have the following structure:
```
bootstrap.servers=localhost:9092
```
Note: this may need to be the following if running kafka locally, this client locally and using the default 
`docker-compose.conf`
```
bootstrap.servers=localhost:9093
```

The `--group` CLI parameter refers to the group ID that the group view is being requested for. This should be the group
ID defined by the group policy file.

The `--member` CLI parameter should be followed by an x500 name for the member whose group view to print. This 
parameter can be repeated multiple times to print multiple member views.