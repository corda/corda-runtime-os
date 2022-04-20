# Running the flow worker
These steps are for running/debugging the flow worker engine using the main docker containers for kafka, DB and 
dependent workers.
(On Windows use WSL to run the steps below)

###  Prerequisites 

Ensure the latest version of the code has been built and published 
```shell
./gradlew publishOSGi -PbaseImage=docker-remotes.software.r3.com/azul/zulu-openjdk
```

You have built a CPB with the flow you want to use for running the flow worker:
1) compile and build the CBP e.g.
```shell
./gradlew testing:cpbs:flow-worker-dev:build
```
2) At the time of writing we need to manually add a policy file, this step should be replaced with build tool in future
versions. 
In the same directory as your built CBP add a file called ```GroupPolicy.json``` (the name is case-sensitive)
in the file put the following json ```{  "groupId": "placeholder" }```


3) Add the policy file to the CBP
```shell
zip [CPB file path]  -j ~/GroupPolicy.json
```
(zip will need to be installed on WSL 'sudo apt-get install zip')

### Start Docker Compose
We only need to run the minimum set of components we need for the flow worker. for dev/debug you can optionally omit
the flow-worker container and run the flow worker jar directly from the IDE.

1) cd into the deploy directory containing the docker compose file
 ```shell
cd applications\workers\release\deploy
```

2) start the containers
```shell
docker-compose up corda-cluster-db rpc-worker init-kafka kafka zookeeper db-worker
```

Currently, all the containers will start quite quickly, but you must check the kafka-init task has completed before you 
attempt to use them. this can take quite a few minutes. Either monitor the container using the docker desktop/ docker cli, 
or the wait for logs to stop moving in the console window used to run the docker-compose command above. 


### Uploading the CBP and creating the Virtual Node

Before we can run a flow we need to upload the CPB and create a virtual node, this involves calling the HTTP APIs, the
instructions below are for using curl.
1) upload the CPI (CPB file created in previous steps, assuming you're running the command from the same directory as the CBP file.)
```shell
curl --insecure -u admin:admin  -s -F upload=@./flow-worker-dev-5.0.0.0-SNAPSHOT-package.cpb https://localhost:8888/api/v1/cpi/
```

This should yield a result similar to this:
```json
{
    "id": "4fd1d5b4-1e95-4e8b-9208-920d26f61f49"
}
```
2) Get the status of the file upload and the cpi file checksum value
```shell
curl --insecure -u admin:admin  https://localhost:8888/api/v1/cpi/status/[ID]
```
where ID is the UUID output from step 1
This should yield are result similar to this
```json
{
   "status":"OK",
   "checksum":"A893413A9921"
}
```
3) Create a virtual node using the checksum returned from the step above
```shell
curl --insecure -u admin:admin -d '{ "request": { "cpiFileChecksum": "A893413A9921", "x500Name": "CN=Testing, OU=Application, O=R3, L=London, C=GB"  } }' https://localhost:8888/api/v1/virtualnode
```

This should yield a result similar to this:
```json
{
  "x500Name": "CN=Testing, OU=Application, O=R3, L=London, C=GB",
  "cpiId": {
    "cpiName": "flow-worker-dev",
    "cpiVersion": "5.0.0.0-SNAPSHOT",
    "signerSummaryHash": null
  },
  "cpiFileChecksum": "36241F2D1E16F158E2CB8559627A6D481D3F358FF5250A2DDF933CF2D454C10E",
  "mgmGroupId": "placeholder",
  "holdingIdHash": "F30413C5C7E2",
  "vaultDdlConnectionId": "d1b8e8a9-c8f1-43dc-ae1d-0f7b9864e07f",
  "vaultDmlConnectionId": "73c26a59-8a65-4169-8582-a184c443dd03",
  "cryptoDdlConnectionId": "ff8b9da4-6643-4951-b8cc-e12e3c90c190",
  "cryptoDmlConnectionId": "fe559d69-e200-45ea-a9a4-b5aafe6ff2d1"
}
```
### Running the Flow Worker
The flow worker can be run from the command line using:
```shell
java -jar build/bin/corda-flow-worker-5.0.0.0-SNAPSHOT.jar --instanceId 1 --messagingParams bus.kafkaProperties.common.bootstrap.servers=localhost:9093 bus.busType=KAFKA
```

or it can be run/debugged direct from intelliJ:
1) Create a run/build configuration of type Jar Application
2) Set the field with the following values
   1) Path to Jar: Set to the flow worker jar in `applications\workers\release\flow-worker\build\bin\`
   2) VM options: '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5008'
   3) Program arguments: '--instanceId=1 -mbus.kafkaProperties.common.bootstrap.servers=localhost:9093 -mbus.busType=KAFKA'
   4) Leaver everything else as-is
3) Add the following gradle task in the Before Launch section 'applications.workers.release.flow-worker.main:appJar'

### Calling the flow and testing for a result

1) Start the flow:
```shell
curl --insecure -u admin:admin -X PUT -d '{ "requestBody": "{\"inputValue\":\"hello\", \"memberInfoLookup\":\"CN=Bob, O=Bob Corp, L=LDN, 
C=GB\", \"throwException\": false }" }' https://localhost:8888/api/v1/flow/[HOLDING_ID_HASH]/request1/net.corda.flowworker.development.flows.TestFlow
```
The holding ID is taken from the output of the 'create virtual node' step

2) Check on the progress of the flow:
```shell
curl --insecure -u admin:admin https://localhost:8888/api/v1/flow/[HOLDING_ID_HASH]/request1
```


