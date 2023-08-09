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
./gradlew :tools:plugins:network:build
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

## Custom Group Parameters (Optional)
Certain properties may be defined in the group policy to be included in the group parameters of a static network. These include the minimum platform version and custom properties containing the prefix "ext.".

To define such group parameters, include them in a `groupParameters` block under `staticNetwork`. For example:
```bash
"groupParameters": {
  "corda.minimum.platform.version": "50000",
  "ext.group.key.0": "value0",
  "ext.group.key.1": "value1"
}
```
> Note: Custom group parameters defined in the group policy cannot be altered or removed. They have a character limit of 128 for keys and 800 for values. A maximum of 100 such key-value pairs may be defined.

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
curl --insecure -u admin:admin -d '{ "memberRegistrationRequest": { "context": { "corda.key.scheme": "CORDA.ECDSA.SECP256R1" } } }' https://localhost:8888/api/v1/membership/<holding identity ID short hash>
```
Run this command for each member defined in the `staticNetwork` section of your _GroupPolicy.json_. 
Perform a lookup to ensure all members have registered successfully and are visible to each other:
```
curl --insecure -u admin:admin -X GET https://localhost:8888/api/v1/members/<holding identity ID short hash>
```
> Note: Only the members who has `ACTIVE` membership status should be visible.

### Registering a member as a notary service representative
To register a member that is acting as a notary service representative, follow the same steps as registering a normal member. However, you should additionally provide the following as part of the `context` when registering:

```
"context": {
    "corda.key.scheme": "CORDA.ECDSA.SECP256R1",
    "corda.roles.0" : "notary",
    "corda.notary.service.name" : \<An X500 name for the notary service\>,
    "corda.notary.service.flow.protocol.name": "com.r3.corda.notary.plugin.nonvalidating",
    "corda.notary.service.flow.protocol.version.0": "1"
}
```

### Registering a member with custom metadata
A member may specify custom properties at the time of registration, which will be included in its MemberInfo. These must be included in the registration context of the member's request to join. Keys of custom properties must have the prefix "ext.". For example:

```bash
"context": {
  "corda.key.scheme": "CORDA.ECDSA.SECP256R1",
  "ext.member.key.0": "value0",
  "ext.member.key.1": "value1"
}
```
> Note: Custom properties have a character limit of 128 for keys and 800 for values. A maximum of 100 such key-value pairs may be defined in the registration context.
