# Network Plugin

The Network Plugin is a powerful tool that extends the functionality of the Corda CLI plugin host for managing membership operations and setting up application networks. It provides a set of commands to interact with an application network, onboard new members, manage group policies, and perform various network-related operations.

## Before you start

To use the Corda Network Plugin, you will need a working copy of `corda-cli` with the mgm and package plugins installed. Here are two options to get started:

### Option 1: Build from Source

1. Clone the `corda-cli-plugin-host` repository from GitHub:

```shell 
git clone https://github.com/corda/corda-cli-plugin-host.git
```

2. Navigate to the `corda-cli-plugin-host` directory:

```shell 
cd corda-cli-plugin-host
```

3. Build the project using Gradle:

```shell 
./gradlew build
```

4. After a successful build, locate the generated JAR file in the `corda-cli-plugin-host/build/libs/` directory.

### Option 2: Download the Final Artifact

1. Go to the [Corda CLI Plugin Host releases page](https://docs.r3.com/en/platform/corda/5.0/deploying-operating/tooling/installing-corda-cli.html) on GitHub.

2. Download the latest release of the Corda CLI Plugin Host JAR file.

Once you have the Corda CLI Plugin Host JAR file, follow these steps to set up the Corda Network Plugin:

1. Create a `plugins` directory in the `corda-cli-plugin-host/build/` directory if it doesn't already exist.

2. Move the downloaded or built Corda Network Plugin JAR file to the `plugins` directory.

With the Corda Network Plugin set up, you can now use the `corda-cli.sh` script to run the commands.

> Note: If you downloaded the final artifact, you can skip the build step and directly move the downloaded JAR file to the `plugins` directory.

For more information on setting up and building the Corda CLI Plugin Host, refer to the [official documentation](https://github.com/corda/corda-cli-plugin-host#setupbuild).

## Basic Commands

The Corda Network Plugin provides the following basic commands:

- `network groupPolicy`: Generates a GroupPolicy.json file for defining the static network section of the GroupPolicy.
- `network dynamic onboard-mgm`: Onboards a new MGM member (and creates a new group) to an existing Corda cluster.
- `network dynamic onboard-member`: Onboards a new standard member to an existing group in the Corda cluster.
- `network lookup members`: Looks up members in an application network based on various filters.
- `network lookup group-parameters`: Looks up group parameters in an application network based on various filters.
- `network get-registrations`: Retrieves the registration information for a member in the network.

For more details on each command and its usage, refer to the command-specific documentation below.

## Setting up an Application Network

To set up an application network using the Corda Network Plugin, follow these steps:

1. Onboard an MGM member to the existing Corda cluster using the `network onboard-mgm` command. This command creates a new group and saves the group policy file.
2. Onboard standard members to the existing group using the `network onboard-member` command. Provide the necessary parameters such as member name, CPB file, etc.
3. Use the `network lookup members` command to verify the members in the network.
4. Use the `network lookup group-parameters` command to view the group parameters of the network.
5. Use the `network get-registrations` command to retrieve the registration information for a member in the network.

# Setting up an Application Network

This guide provides step-by-step instructions on how to set up an application network using the Corda Network Plugin. The application network consists of an MGM, standard members, and a notary.

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
   - Run the `network dynamic onboard-mgm` command with the necessary parameters to onboard the MGM member and create a new group.
   - Specify the member's details, such as name, user, password, target URL, etc.

Example Commands
```bash
./corda-cli.sh network dynamic onboard-mgm 'O=MGM, L=London, C=GB' --user=admin --password=admin --target=https://localhost:8888 --insecure
```
- Use `./corda-cli.sh network dynamic onboard-mgm --help` to view the other options.

Example Command
```bash
./corda-cli.sh network dynamic onboard-mgm 'O=MGM, L=London, C=GB' --save-group-policy-as /tmp/groupPolicy.json --user=admin --password=admin --target=https://localhost:8888 --insecure
```

2. Onboard a member:
   - Run the `network dynamic onboard-member` command with the necessary parameters to onboard standard members to the existing group.
   - Specify the member's details, such as name, CPB file, user, password, target URL, etc.
   - Optionally, use the `--wait` parameter to wait until the request gets approved/declined.
  
Example Command
```bash
./corda-cli.sh network dynamic onboard-member 'O=Alice, L=London, C=GB' --cpb-file <path-to-your-CPB-file> --user=admin --password=admin --target=https://localhost:8888 --insecure
```

- Use `./corda-cli.sh network dynamic onboard-member --help` to view the other options.

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

   - Use `--help` for more details on available filters and options.

4. View group parameters:
   - Use the `network lookup group-parameters` command to view the group parameters of the network.
   - Specify filters such as holding identity short hash, name, group, etc., to narrow down the search.
   - Use `--help` for more details on available filters and options.

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

5. Retrieve registration information:
   - Use the network get-registrations command to retrieve the registration information for a member in the network.
   - Specify the member's identity short hash to retrieve the registration information. 
   
Example Command: 

To retrieve the registration information for member 3B8DECDDD6E2:

```bash
./corda-cli.sh network get-registrations -h "3B8DECDDD6E2" --user=admin --password=admin --target=https://localhost:8888 --insecure
```

Use --help for more details on available options.