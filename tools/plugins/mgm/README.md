# MGM Plugin
This is a plug-in for [Corda CLI plugin host](https://github.com/corda/corda-cli-plugin-host) for membership operations.

> The commands below assume you have access to corda-cli.sh   
> To use this plugin, build the JAR with `./gradlew :tools:plugins:mgm:build`, move the JAR to 
> `corda-cli-plugin-host/build/plugins/` and run the commands as shown below by locating corda-cli.sh in 
> `corda-cli-plugin-host/build/generatedScripts/`.

# Generate Group Policy

This is a sub-command under the `mgm` plugin for generating a GroupPolicy.json file.

Running `groupPolicy` without any command line arguments prints a sample GroupPolicy file for the user to manually tweak.
```shell
./corda-cli.sh mgm groupPolicy
```

Alternatively, the following command line arguments can be used to define the static network section of the GroupPolicy:

| Argument            | Description                                                          |
|---------------------|----------------------------------------------------------------------|
| --file, -f          | Path to a JSON or YAML file that contains static network information |
| --name              | Member's X.500 name                                                  |
| --endpoint          | Endpoint base URL                                                    |
| --endpoint-protocol | Version of end-to-end authentication protocol                        |

To generate GroupPolicy using file input:
> Sample files are available [here](#sample-files).
```shell
./corda-cli.sh mgm groupPolicy --file="app/build/resources/src.yaml"
```
Note:
1. Only one of `memberNames` and `members` blocks may be present.
2. Single endpoint is assumed for all members when `memberNames` is used.
3. Endpoint information specified under `members` overrides endpoint information set at the root level. An error is thrown if endpoint information is not provided at all.

To generate GroupPolicy using string parameters:
```shell
./corda-cli.sh mgm groupPolicy --name="C=GB, L=London, O=Member1" --name="C=GB, L=London, O=Member2" --endpoint-protocol=5 --endpoint="http://dummy-url"
```
Note:
1. Passing one or more `--name` without specifying endpoint information will throw an error.
2. Not passing any `--name` will return a GroupPolicy with an empty list of static members.
3. Single endpoint is assumed for all members.

## Sample files

1. Sample JSON with `memberNames`
```json
{
  "endpoint": "http://dummy-url",
  "endpointProtocol": 5,
  "memberNames": ["C=GB, L=London, O=Member1", "C=GB, L=London, O=Member2"]
}
```

2. Sample JSON with `members`
```json
{
  "members": [
    {
      "name": "C=GB, L=London, O=Member1",
      "status": "PENDING",
      "endpoint": "http://dummy-url",
      "endpointProtocol": 5
    },
    {
      "name": "C=GB, L=London, O=Member2",
      "endpoint": "http://dummy-url2",
      "endpointProtocol": 5
    }
  ]
}
```

3. Sample YAML with `memberNames`
```yaml
endpoint: "http://dummy-url"
endpointProtocol: 5
memberNames: ["C=GB, L=London, O=Member1", "C=GB, L=London, O=Member2"]
```

4. Sample YAML with `members` which all use a common endpoint, and Member1 overrides the protocol version
```yaml
endpoint: "http://dummy-url"
endpointProtocol: 5
members:
    - name: "C=GB, L=London, O=Member1"
      status: "PENDING"
      endpointProtocol: 10
    - name: "C=GB, L=London, O=Member2"
```

# Setup Kubernetes networks

This is a sub-command under the `mgm` plugin for setting up Kubernetes networks. 

Running `setupNetwork` with the name of the networks to create. For example:
```shell
./corda-cli.sh mgm setupNetwork demo-network-one demo-network-two
```
By default, it will use the latest released tag. Change it using the `--baseImage` option. It will delete any
existing network with that name. Use the `--help` to view all the other options.

# Onboard a member to an existing network
This is a sub-command under the `mgm` plugin for on-boarding a member (MGM or standard member) into a running network.

To run the network either use the `setupNetwork` command or run a combine worker locally ([see here](../../../applications/workers/release/combined-worker/README.md)).

## Onboard an MGM member to an existing network
This is a sub-command under the `onboard` sub-command to on board a new MGM member (and create a new group).

To onboard on a Kubernetes network use the network name as parameter. By default, it will try to on board on a combined worker.
Use the `--save-group-policy-as` to indicate where to save the MGM group policy file (that can be used to create CPIs - [see here](../package/README.md))

An example of on-boarding an MGM can be:
```shell
./corda-cli.sh mgm onboard mgm demo-network-one  --x500-name='O=MGM, L=London, C=GB'
```
Use the `--help` to view all the other options.

See [here](https://github.com/corda/corda-runtime-os/wiki/MGM-Onboarding) for details on how to do it manually.

## Onboard a standard member to an existing network
This is a sub-command under the `onboard` sub-command to on board a new member to an existing group.

To onboard on a Kubernetes network use the network name as parameter. By default, it will try to on board on a combined worker.
To decide which CPI to use, there are three options:
* If you know the CPI hash, you can use it with the `--cpi-hash` option
* If you have the CPI file (for example, from the [package command](../package/README.md)), you can use it with the `--cpi-file` option.
* If you have a CPB and a group policy file (from the `onboard mgm` command), you can use the `--cpb-file` and `--group-policy-file` option. This will create an unsigned CPI and save it in your home directory.

An example of on-boarding a member can be:
```shell
./corda-cli.sh mgm onboard member demo-network-two --x500-name='O=Alice, L=London, C=GB' --cpb-file ~/corda-runtime-os/testing/cpbs/chat/build/libs/*.cpb
./corda-cli.sh mgm onboard member demo-network-one --x500-name='O=Bob, L=London, C=GB' --cpb-file ~/corda-runtime-os/testing/cpbs/chat/build/libs/*.cpb
```
Use the `--help` to view all the other options.

See [here](https://github.com/corda/corda-runtime-os/wiki/Member-Onboarding-(Dynamic-Networks)) for details on how to do it manually.

