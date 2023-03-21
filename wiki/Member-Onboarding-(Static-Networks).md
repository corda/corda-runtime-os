This page describes the onboarding process for static members. To understand the basic concept behind the static network, see the  [Membership](../wiki/Membership) wiki.

> Note: Static Networks does not require an MGM. If you will need to use an MGM then please follow the instructions for [Dynamic Onboarding](https://github.com/corda/corda-runtime-os/wiki/MGM-Onboarding).

## Deploy Corda 5 to Kubernetes
To build and deploy Corda 5 to a Kubernetes cluster, follow the instructions for [Local development with Kubernetes](https://github.com/corda/corda-runtime-os/wiki/Local-development-with-Kubernetes).

## Create the group policy
Create a directory to store your files.
```
mkdir -p ~/Desktop/register-member/
```

Build the `mgm` plugin in the [corda-runtime-os](https://github.com/corda/corda-runtime-os) repo using the following command:
```
./gradlew :tools:plugins:mgm:build
```

Build the [corda-cli-plugin-host](https://github.com/corda/corda-cli-plugin-host) repo:
```
./gradlew build
```

Copy `mgm-5.0.0.0-SNAPSHOT.jar` from `corda-runtime-os`:
```
cd tools/plugins/mgm/build/libs
```

To the `corda-cli-plugin-host` repo's `build/plugins` directory. Run the following command to generate your `GroupPolicy` file:
```
./build/generatedScripts/corda-cli.sh mgm groupPolicy --name="C=GB, L=London, O=Alice" --name="C=GB, L=London, O=Bob" --name="C=GB, L=London, O=Charlie" --endpoint-protocol=1 --endpoint="http://localhost:1080" > ~/Desktop/register-member/GroupPolicy.json
```

For more options to generate `GroupPolicy` file follow [this](https://github.com/corda/corda-runtime-os/blob/release/os/5.0/tools/plugins/mgm/README.md) readme.

## Create a CPI

Create a CPI by following the steps outlined here: [CorDapp Packaging](../wiki/CorDapp-Packaging)
When following these steps, be sure to use the group policy file generated previously.

## Upload the CPI
The name of the CPI generate will vary depending on your setup. The doc will now continue under the assumption that the CPI is call `myCpi.cpi`, so please replace this with the name of your CPI.
```
export CPI_PATH=./myCpi.cpi
curl --insecure -u admin:admin -F upload=@./$CPI_PATH https://localhost:8888/api/v1/cpi/
```

The returned identifier (e.g. the return will look like `{"id":"f0a0f381-e0d6-49d2-abba-6094992cef02"}` and the identifier is the id, or, using jq, one can run `CPI_ID=$(curl --insecure -u admin:admin -F upload=@$CPI_PATH $API_URL/cpi | jq -r '.id')`) ) is the `CPI ID`, use it below to get the checksum of the CPI
```
curl --insecure -u admin:admin https://localhost:8888/api/v1/cpi/status/<CPI ID>
```
The result contains the `CPI checksum`. Save this for the next step.

## Create virtual nodes for each member
Next is to create a virtual node for each member using the `CPI checksum`. Save the `holding identity ID short hash`s obtained from this step.
```
curl --insecure -u admin:admin -d '{ "request": { "cpiFileChecksum": "<CPI checksum>", "x500Name": "<member's name>"  } }' https://localhost:8888/api/v1/virtualnode
```

## Register members
> The available key schemes are viewable through `KeysRpcOps`. One of them is used as an example in the command below.

To register a member, run the following command (replace `<holding identity ID short hash>` with the ID short hash obtained before):
```
curl --insecure -u admin:admin -d '{ "memberRegistrationRequest": { "action": "requestJoin", "context": { "corda.key.scheme": "CORDA.ECDSA.SECP256R1" } } }' https://localhost:8888/api/v1/membership/<holding identity ID short hash>
```
Run this command for each member defined in the `staticNetwork` section of your _GroupPolicy.json_. 
Perform a lookup to ensure all members have registered successfully and are visible to each other:
```
curl --insecure -u admin:admin -X GET https://localhost:8888/api/v1/members/<holding identity ID short hash>
```
> Note: Only the members who has `ACTIVE` membership status should be visible.

### Registering a member as a notary service representative
To register a member that is acting as a notary service representative, follow the same steps as registering a normal member. However, you should additionally provide the following as part of the `context` when registering:

"context": { "corda.key.scheme": "CORDA.ECDSA.SECP256R1", __"corda.roles.0" : "notary", "corda.notary.service.name" : \<An X500 name for the notary service\>, "corda.notary.service.plugin" : "net.corda.notary.NonValidatingNotary"__ }