# Corda CLI

Corda CLI is a command-line interface for interacting with and configuring Corda and/or Corda dependencies.

## Usage
```shell
Usage: corda-cli [-hV] [COMMAND]
  -h, -?, -help, --help   Display help and exit.
  -V, --version           Display version and exit.
Commands:
  cpi             CPI(Corda Package Installer) related operations
  database        Does Database bootstrapping and upgrade
  initial-config  Create SQL files to write the initial config to a new cluster
  initial-rbac    Creates common RBAC roles
  mgm             Plugin for membership operations.
  package         Plugin for CPB, CPI operations.
  preinstall      Preinstall checks for Corda.
  secret-config   Generate secret Config values which can be inserted into your
                    Corda Config, removing the need to put sensitive values in
                    plain text. The output will depend on the type of secrets
                    service being used. See 'type' for more information.
  topic           Plugin for Kafka topic operations.
  vnode           Manages a virtual node

```

## Commands

The Corda CLI has the following plugins:

- [cpi](../../plugins/cpi/README.md)
- [database](../../plugins/db-config)
- [initial-config](../../plugins/initial-config/README.md)
- [initial-rbac](../../plugins/initial-rbac/README.md)
- [mgm](../../plugins/network/README.md)
- [package](../../plugins/package/README.md)
- [preinstall](../../plugins/preinstall/README.md)
- [secret-config](../../plugins/secret-config/README.md)
- [topic](../../plugins/topic-config/)
- [vnode](../../plugins/virtual-node/README.md)

## Setup/Build

Run `./gradlew :tools:corda-cli:build`
This will create the following in this module's build directory:
- `corda-cli.jar` in the `build/cli/` directory, including start and install scripts
- raw jar in the `build/libs/` directory
- `corda-cli-dist.zip` in the `build/zip/` directory

## Testing
Smoke tests in individual Corda CLI command directories under `pluginsSmokeTest` are run against the Combined Worker, intended to be triggered manually during development. There is also a nightly Jenkins job that runs these tests on the release branch. In the future, it may be included as a PR-gate.
