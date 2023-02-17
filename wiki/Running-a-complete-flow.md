Currently (2022-05-18), we only support running CorDapp 'flows'. 

To run a CorDapp flow:
1. [Create a CPI file from the CPB file generated for the CorDapp.](#Create-a-CPI-from-a-CorDapp)
2. [Upload the CPI to your corda cluster.](##Uploading-a-CPI)
3. [Create a virtual node in the corda cluster to run the CorDapp.](#Create-a-virtual-node)

   [You can now start flows and collect results from completed flows.](#Execute-flow)
 
# Prerequisites

* Linux/MacOS/WSL:
  * `curl`, `jq`
* Windows (and Linux/MacOS)
   * Powershell 7.x
* a built Cordapp with the flow you want to run 
* A running Corda cluster. [You can run a corda cluster locally on kubernetes.](https://github.com/corda/corda-runtime-os/wiki/Local-development-with-Kubernetes)
* corda-cli — [a tool that implements various utilities for managing Corda and CorDapps](https://github.com/corda/corda-cli-plugin-host/tree/release/version-1.0.0/plugins/package).

# Known Issues

The following are known issues to be fixed in the future:

* You must allow some time for the `CPI` to arrive and be unpacked in the `flow-worker` after an upload.
* Only the first `CPI` uploaded is accepted, if two or more `CPIs` only differ by `GroupPolicy.json`.  Subsequent uploads seem to 'succeed' but do not.

# Create a CPI from a CorDapp 

[To create a CPI, follow the instructions in the corda/corda-cli-plugin-host repository.](https://github.com/corda/corda-cli-plugin-host/tree/release/version-1.0.0/plugins/package)

## Uploading a CPI

The following instructions assume you are using bash. The scripts in the [Scripts for testing](Scripts-for-testing) section echo these steps again in bash but also in PowerShell v7.

Set the CPI environment variable to the location of your CPI file:

```shell
CPI=<path-to-CPI-file>
```

```shell
curl --insecure -u admin:admin  -s -F upload=@$CPI https://localhost:8888/api/v1/cpi/
```

Again, but capturing the REQUEST_ID from the JSON reponse from the cluster.

```shell
read REQUEST_ID < <(echo $(curl --insecure -u admin:admin  -s -F upload=@$CPI https://localhost:8888/api/v1/cpi/ | jq -r '.id'))
```

You need `REQUEST_ID` to [determine the status](#Checking-the-status-of-the-CPI-and-getting-the-CPI Hash), which returns the `CPI_HASH` and allows you to create a virtual node.

## Checking the status of the CPI and getting the CPI Hash

Use the `cpi/status/:requestId` end point:

```shell
# curl --insecure -u admin:admin  https://localhost:8888/api/v1/cpi/status/6eb3bec9-791e-4695-80e1-cdb5337e0db9

curl --insecure -u admin:admin  https://localhost:8888/api/v1/cpi/status/$REQUEST_ID
```

# Create a virtual node
**Note:** Pick one of the following two methods. Attempting to create the virtual node a second time results in an error that will not contain the holding ID hash that is needed.

Given some `X500` name and a `CPI_HASH` (of a successful upload) we can create a virtual node:

```shell
curl --insecure -u admin:admin -d '{ "request": { "cpiFileChecksum": "0123456789ab", "x500Name": "CN=Testing, OU=Application, O=R3, L=London, C=GB"  } }' https://localhost:8888/api/v1/virtualnode
```

_Alternatively_, using variable capture from above:

```shell
X500="CN=Testing, OU=Application, O=R3, L=London, C=GB"

# Capture the HOLDING_ID
HOLDING_ID=$(curl --insecure -u admin:admin -s -d '{ "request": { "cpiFileChecksum": "'"$CPI_HASH"'", "x500Name": "'"$X500"'"  } }' https://localhost:8888/api/v1/virtualnode | jq -r '.holdingIdHash')
```

**Note:** Use quotes as `bash` treats everything as text inside single quotes.

# Register Member to the network (if flow uses sessions)

If a member wishes to send or receive a message as part of a flow session they must be registered to the network. For static networks the member must also be included in the GroupPolicy file. This can be done via the [corda-cli tool.](https://github.com/corda/corda-cli-plugin-host/tree/release/version-1.0.0/plugins/package) 

```shell
#Register member to network
curl --insecure -u admin:admin -d '{ "memberRegistrationRequest": { "action": "requestJoin",  "context": { "corda.key.scheme" : "CORDA.ECDSA.SECP256R1" } } }' https://localhost:8888/api/v1/membership/$HOLDING_ID 
```

# Execute flow

```shell
curl --insecure -u admin:admin \
  -X 'POST' \
  https://localhost:8888/api/v1/flow/$HOLDING_ID \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "startFlow": {
    "clientRequestId": "r1",
    "flowClassName": "net.cordapp.testing.calculator.CalculatorFlow",
    "requestBody": "{ \"a\":10, \"b\":20 }"
  }
}'
```

## Check flow status

```shell
curl --insecure -u admin:admin https://localhost:8888/api/v1/flow/$HOLDING_ID/r1
```
# Scripts for testing

## Bash script

We assume each step just works:

```shell
### Change this line though!

$CPI=<path-to-CPI-file>

#  Upload CPI
read REQUEST_ID < <(echo $(curl --insecure -u admin:admin  -s -F upload=@$CPI https://localhost:8888/api/v1/cpi/ | jq -r '.id'))
printf "\nRequest id = $REQUEST_ID  \n\n"

#  Check status
sleep 1
read STATUS CPI_HASH < <(echo $(curl --insecure -u admin:admin -s https://localhost:8888/api/v1/cpi/status/$REQUEST_ID | jq -r '.status, .checksum'))
printf "\nRequest id = $REQUEST_ID   CPI hash = $CPI_HASH   Status = $STATUS\n\n"

#  Create virtual node
X500_BOB="CN=Bob, OU=Application, O=R3, L=London, C=GB"

HOLDING_ID_BOB=$(curl --insecure -u admin:admin -s -d '{ "request": { "cpiFileChecksum": "'"$CPI_HASH"'", "x500Name": "'"$X500_BOB"'"  } }' https://localhost:8888/api/v1/virtualnode | jq -r '.holdingIdHash')
printf "\nHolding id = $HOLDING_ID_BOB\n\n"


#  WARNING - wait here for a bit for the cpk to arrive in the flow worker
sleep 5


# Start a flow
curl --insecure -u admin:admin \
  -X 'POST' \
  https://localhost:8888/api/v1/flow/$HOLDING_ID \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "startFlow": {
    "clientRequestId": "r1",
    "flowClassName": "net.cordapp.testing.calculator.CalculatorFlow",
    "requestBody": "{ \"a\":10, \"b\":20 }"
  }
}'



# Check status
sleep 2
curl --insecure -u admin:admin https://localhost:8888/api/v1/flow/status/$HOLDING_ID_BOB/r1


## BONUS:  SECOND VNODE
X500_ALICE="CN=Alice, OU=Application, O=R3, L=London, C=GB"
HOLDING_ID_ALICE=$(curl --insecure -u admin:admin -s -d '{ "request": { "cpiFileChecksum": "'"$CPI_HASH"'", "x500Name": "'"$X500_ALICE"'"  } }' https://localhost:8888/api/v1/virtualnode | jq -r '.holdingIdHash')
printf "\nHolding id = $HOLDING_ID_ALICE\n\n"


curl --insecure -u admin:admin \
  -X 'POST' \
  https://localhost:8888/api/v1/flow/$HOLDING_ID_ALICE \
  -H 'accept: application/json' \
  -H 'Authorization: Basic YWRtaW46YWRtaW4=' \
  -H 'Content-Type: application/json' \
  -d '{
  "startFlow": {
    "clientRequestId": "r1",
    "flowClassName": "net.cordapp.testing.calculator.CalculatorFlow",
    "requestBody": "{ \"a\":10, \"b\":20 }"
  }
}'


sleep 2
curl --insecure -u admin:admin https://localhost:8888/api/v1/flow/$HOLDING_ID_ALICE/r1

```


## Powershell 7.x

Works on Windows, and even Linux/MacOS

```powershell

### Change this line!
$CPI=<path-to-CPI-file>

# Upload the CPI
$JSON = curl --insecure -u admin:admin  -s -F upload=@$CPI https://localhost:8888/api/v1/cpi/
$REQUEST_ID =(echo $JSON | ConvertFrom-Json).id

echo "Request id = $REQUEST_ID"

# Check upload status
sleep 2
$JSON = curl --insecure -u admin:admin  https://localhost:8888/api/v1/cpi/status/$REQUEST_ID
$CPI_HASH =(echo $JSON | ConvertFrom-Json).cpiFileChecksum
echo "Request id = $REQUEST_ID   CPI Hash = $CPI_HASH"

# Create Virtual Node
$X500_BOB="CN=Bob, OU=Application, O=R3, L=London, C=GB"
$PAYLOAD=@{ request = @{ cpiFileChecksum = "$CPI_HASH"; x500Name = "$X500_BOB" } }
$JSON = $PAYLOAD | ConvertTo-Json | curl --insecure -u admin:admin -s -d '@-' https://localhost:8888/api/v1/virtualnode
$HOLDING_ID_BOB =(echo $JSON | ConvertFrom-Json).holdingIdHash
echo "Holding id = $HOLDING_ID_BOB"

#  WARNING - wait here for a bit for the cpk to arrive in the flow-worker
sleep 5

# Start Flow - TODO needs to be the same as bash
# $PAYLOAD=@{ requestBody= "`{ `"a`":10, `"b`":20 `}" }
# $JSON = $PAYLOAD | ConvertTo-Json | curl --insecure -u admin:admin -s -X PUT -d '@-' https://localhost:8888/api/v1/flow/$HOLDING_ID_BOB/r1/net.cordapp.testing.calculator.CalculatorFlow
#echo $JSON
#echo ""
sleep 1

# Check flow status
curl --insecure -u admin:admin https://localhost:8888/api/v1/flow/$HOLDING_ID_BOB/r1
```
