This is a tool that can be used to push a group policy file for members to kafka. This is a short term and temporary 
solution for allowing us to demo and test membership functionality while the CPI build and upload functionality is not
available. 

This tool creates dummy CPI information with the provided group policy file and pushes it to kafka for the membership 
services to pick up.  

## Building the tool
```
./gradlew applications:tools:mgm-test:group-policy-uploader:clean
./gradlew applications:tools:mgm-test:group-policy-uploader:appJar
```

## Running the tool

```
java -jar applications/tools/mgm-test/group-policy-uploader/build/bin/corda-group-policy-uploader-5.0.0.0-SNAPSHOT.jar \
    --policy-file ~/Desktop/GroupPolicy.json --kafka ~/Desktop/kafka.properties --member "C=GB, L=London, O=Alice" \
    --member "C=GB, L=London, O=Bob" --member "C=GB, L=London, O=Charlie"
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

The file provided on the `--policy-file` CLI parameter should be generated from the corda-cli tool, or samples can be 
found in integration tests. 

The `--member` CLI parameter should be followed by an x500 name for the member the policy file belongs to. This 
parameter can be repeated multiple times.