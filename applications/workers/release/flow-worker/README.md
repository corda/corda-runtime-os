# Running the flow worker

These steps are for running/debugging the flow worker engine using the main docker containers for kafka, DB and
dependent workers.
(On Windows use WSL to run the steps below)

### Prerequisites

Ensure the latest version of the code has been built and published

```shell
./gradlew publishOSGi -PbaseImage=docker-remotes.software.r3.com/azul/zulu-openjdk
```

You have built a CPB with the flow you want to use for running the flow worker:

1) compile and build the CBP e.g.

```shell
./gradlew testing:cpbs:test-cordapp:build
```

2) Generate a GroupPolicy file for the CPB. This can be done using the
   corda-cli.  https://github.com/corda/corda-cli-plugin-host/tree/release/version-1.0.0/plugins/package
   More information about GroupPolicy files can be found at https://github.com/corda/corda-runtime-os/wiki/Group-Policy.
   Example command to generate a GroupPolicy:

```shell
./gradlew build
./build/generatedScripts/corda-cli.sh mgm groupPolicy --name="CN=Alice, OU=Application, O=R3, L=London, C=GB" --name="CN=Bob, OU=Application, O=R3, L=London, C=GB" --endpoint-protocol=1 --endpoint="http://localhost:1080" >> GroupPolicy.json
```  

In the same directory as your built CBP add the file called ```GroupPolicy.json``` (the name is case-sensitive)

3) Add the policy file to the CBP

```shell
zip [CPB file path]  -j ~/GroupPolicy.json
```

(zip will need to be installed on WSL 'sudo apt-get install zip')

### Deploy Workers

Follow the helm charts under the /charts directory to deploy the workers, postgres and kafka.

Example run:

```shell
kubectl delete ns corda
kubectl config get-contexts
kubectl config use-context docker-desktop
kubectl create namespace corda
kubectl config set-context --current --namespace=corda

helm install prereqs -n corda `
  oci://corda-os-docker.software.r3.com/helm-charts/corda-prereqs `
  --set kafka.replicaCount=1,kafka.zookeeper.replicaCount=1 `
  --render-subchart-notes `
  --timeout 10m `
  --wait

helm upgrade --install corda -n corda `
  oci://corda-os-docker.software.r3.com/helm-charts/corda `
  --version ^0.1.0-beta `
  --values values.yaml `
  --wait

kubectl port-forward --namespace corda deployment/corda-rest-worker 8888
```

### Uploading the CBP and creating the Virtual Node

Before we can run a flow we need to upload the CPB and create a virtual node, this involves calling the HTTP APIs, the
instructions below are for using curl.

1) upload the CPI (CPB file created in previous steps, assuming you're running the command from the same directory as
   the CBP file.)

```shell
curl --insecure -u admin:admin  -s -F upload=@./test-cordapp-5.0.0.0-SNAPSHOT-package.cpb https://localhost:8888/api/v1/cpi/

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
  "status": "OK",
  "checksum": "B669663F74EA"
}
```

3) Create a virtual node using the checksum returned from the step above

```shell
curl --insecure -u admin:admin -d '{ "cpiFileChecksum": "B669663F74EA", "x500Name": "C=GB, L=London, O=Alice" }' https://localhost:8888/api/v1/virtualnode
curl --insecure -u admin:admin -d '{ "cpiFileChecksum": "B669663F74EA", "x500Name": "C=GB, L=London, O=Bob" }' https://localhost:8888/api/v1/virtualnode
```

This should yield a result similar to this for first request:

```json
{
  "x500Name": "C=GB, L=London, O=Alice",
  "cpiId": {
    "cpiName": "test-cordapp",
    "cpiVersion": "5.0.0.0-SNAPSHOT",
    "signerSummaryHash": null
  },
  "cpiFileChecksum": "36241F2D1E16F158E2CB8559627A6D481D3F358FF5250A2DDF933CF2D454C10E",
  "mgmGroupId": "placeholder",
  "holdingIdShortHash": "3B8DECDDD6E2",
  "vaultDdlConnectionId": "d1b8e8a9-c8f1-43dc-ae1d-0f7b9864e07f",
  "vaultDmlConnectionId": "73c26a59-8a65-4169-8582-a184c443dd03",
  "cryptoDdlConnectionId": "ff8b9da4-6643-4951-b8cc-e12e3c90c190",
  "cryptoDmlConnectionId": "fe559d69-e200-45ea-a9a4-b5aafe6ff2d1"
}
```

4) Register the members to the network

```shell
curl --insecure -u admin:admin -d '{ "action": "requestJoin",  "context": { "corda.key.scheme" : "CORDA.ECDSA.SECP256R1" } }' https://localhost:8888/api/v1/membership/3B8DECDDD6E2
curl --insecure -u admin:admin -d '{ "action": "requestJoin",  "context": { "corda.key.scheme" : "CORDA.ECDSA.SECP256R1" } }' https://localhost:8888/api/v1/membership/44D0F817B592
```

### Calling the flow and testing for a result

1) Start the flow:

```shell
curl --insecure -u admin:admin -X 'POST' \
  'https://localhost:8888/api/v1/flow/3B8DECDDD6E2' \
  -d '{
    "clientRequestId": "request1",
    "flowClassName": "net.cordapp.testing.testflows.MessagingFlow",
    "requestData": "{\"counterparty\": \"C=GB, L=London, O=Bob\"}"
}'
```

The holding ID is taken from the output of the 'create virtual node' step

2) Check on the progress of the flow:

```shell
curl --insecure -u admin:admin https://localhost:8888/api/v1/flow/[HOLDING_ID_HASH]/request1
```
