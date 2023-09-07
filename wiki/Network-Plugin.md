# Corda Network Plugin

The Corda Network Plugin is a powerful tool that extends the functionality of the Corda CLI plugin host for managing membership operations and setting up application networks. It provides a set of commands to interact with an application network, onboard new members, manage group policies, and perform various network-related operations.

## Before you start

You will need a working copy of `corda-cli` with the mgm and package plugins installed. See https://github.com/corda/corda-cli-plugin-host#setupbuild.

## Basic Commands

The Corda Network Plugin provides the following basic commands:

- `network group-policy`: Generates a GroupPolicy.json file for defining the static network section of the GroupPolicy.
- `network onboard-mgm`: Onboards a new MGM member (and creates a new group) to an existing Corda cluster.
- `network onboard-member`: Onboards a new standard member to an existing group in the Corda cluster.
- `network lookup members`: Looks up members in an application network based on various filters.
- `network lookup group-parameters`: Looks up group parameters in the Corda network based on various filters.

For more details on each command and its usage, refer to the command-specific documentation below.

## Setting up an Application Network

To set up an application network using the Corda Network Plugin, follow these steps:

1. Onboard an MGM member to the existing Corda cluster using the `network onboard-mgm` command. This command creates a new group and saves the group policy file.
2. Onboard standard members to the existing group using the `network onboard-member` command. Provide the necessary parameters such as member name, CPB file, etc.
3. Use the `network lookup members` command to verify the members in the network.
4. Use the `network lookup group-parameters` command to view the group parameters of the network.

# Setting up an Application Network

This guide provides step-by-step instructions on how to set up an application network using the Corda Network Plugin. The application network consists of an MGM member, standard members, and a notary.

## Prerequisites

Before setting up the application network, ensure that you have completed the following prerequisites:

- Installed the Corda Network Plugin as described in the [Installation](#installation) section.
- Familiarized yourself with the basic commands of the Corda Network Plugin as described in the [Basic Commands](#basic-commands) section.

## Steps

### Installation

To use the Corda Network Plugin, follow these steps:

1. Build the JAR file by running 
```bash
./gradlew :tools:plugins:network:build
```
4. Move the generated JAR file to the `corda-cli-plugin-host/build/plugins/` directory.
5. Locate the `corda-cli.sh` script in the `corda-cli-plugin-host/build/generatedScripts/` directory.
6. Run the commands using the `corda-cli.sh` script.

Follow these steps to set up an application network:

1. Onboard an MGM member:
   - Run the `network onboard-mgm` command with the necessary parameters to onboard the MGM member and create a new group.
   - Specify the member's details, such as name, user, password, target URL, etc.

Example Commands
```bash
./corda-cli.sh network dynamic onboard-mgm 'O=MGM, L=London, C=GB' --user=admin --password=admin --target=https://localhost:8888 --insecure
```
- Use `./corda-cli.sh network dynamic onboard-mgm --help` to view the other options.
- Optionally, use the `--save-group-policy-as` parameter to specify a custom location to save the MGM group policy file.

Example Command
```bash
./corda-cli.sh network dynamic onboard-mgm 'O=MGM, L=London, C=GB' --save-group-policy-as /tmp/groupPolicy.json --user=admin --password=admin --target=https://localhost:8888 --insecure
```

2. Onboard standard members:
   - Run the `network onboard-member` command with the necessary parameters to onboard standard members to the existing group.
   - Specify the member's details, such as name, CPB file, user, password, target URL, etc.
   - Optionally, use the `--wait` parameter to wait until the request gets approved/declined.
  
Example Command
```bash
./corda-cli.sh network dynamic onboard-member 'O=Alice, L=London, C=GB' --cpb-file ~/corda-runtime-os/testing/cpbs/chat/build/libs/*.cpb --user=admin --password=admin --target=https://localhost:8888 --insecure
```

- Use `./corda-cli.sh network dynamic onboard-mgm --help` to view the other options.

3. Verify the network:
   - Use the `network lookup members` command to verify the members in the network.
   - Specify filters such as holding identity short hash, name, group, etc., to narrow down the search.

Example Commands:

To look up all members visible to member `3B8DECDDD6E2`:

```bash
./corda-cli.sh network lookup members -h "3B8DECDDD6E2"
```

To look up members visible to member `3B8DECDDD6E2` filtered by attributes Organization (O) `Alice` and Country (C) `IE`:

```bash
./corda-cli.sh network lookup members -h "3B8DECDDD6E2" -o "Alice" -c "IE"
```

To look up all members visible to `C=GB, L=London, O=Member1` from the default (last created) group:

```bash
./corda-cli.sh network lookup members -n "C=GB, L=London, O=Member1"
```

To look up all members visible to `C=GB, L=London, O=Member1` from group `b0a0f381-e0d6-49d2-abba-6094992cef02`:

```bash
./corda-cli.sh network lookup members -n "C=GB, L=London, O=Member1" -g "b0a0f381-e0d6-49d2-abba-6094992cef02"
``` 

To look up suspended members as the MGM (`1B8DECDDD6E2`) from the default group:

```bash
./corda-cli.sh network lookup members -h "1B8DECDDD6E2" -s "SUSPENDED"
```

   - Refer to the [Lookup Members Wiki](https://github.com/corda/platform-eng-design/blob/5d75cf18e6df4eb8044abf53d9c4e2c62d62ef8b/core/corda-5/corda-5.0/the-host/network-plugin.md#lookup-members) for more details on available filters and options.

4. View group parameters:
   - Use the `network lookup group-parameters` command to view the group parameters of the network.
   - Specify filters such as holding identity short hash, name, group, etc., to narrow down the search.
   - Refer to the [Lookup Group Parameters Wiki](https://github.com/corda/platform-eng-design/blob/5d75cf18e6df4eb8044abf53d9c4e2c62d62ef8b/core/corda-5/corda-5.0/the-host/network-plugin.md#lookup-group-parameters) for more details on available filters and options.

Example Commands:

To look up group parameters visible to member `3B8DECDDD6E2`:

```bash
./corda-cli.sh network lookup group-parameters -h "3B8DECDDD6E2" --user=admin --password=admin --target=https://localhost:8888 --insecure
```

To look up group parameters visible to `C=GB, L=London, O=Member1` from the default (last created) group:

```bash
./corda-cli.sh network lookup group-parameters -n "C=GB, L=London, O=Member1" --user=admin --password=admin --target=https://localhost:8888 --insecure
``` 

To look up group parameters visible to `C=GB, L=London, O=Member1` from group `b0a0f381-e0d6-49d2-abba-6094992cef02`:

```bash
./corda-cli.sh network lookup group-parameters -n "C=GB, L=London, O=Member1" -g "b0a0f381-e0d6-49d2-abba-6094992cef02" --user=admin --password=admin --target=https://localhost:8888 --insecure
```

## Conclusion

By following these steps, you have successfully set up an application network using the Corda Network Plugin. You can now manage membership, onboard new members, and perform various network-related operations.
