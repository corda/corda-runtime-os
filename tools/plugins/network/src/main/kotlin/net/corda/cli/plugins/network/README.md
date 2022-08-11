# Network Plugin

This is a plug-in for [Corda CLI plugin host](https://github.com/corda/corda-cli-plugin-host) for interacting with a
network.

> The commands below assume you have access to corda-cli.sh   
> To use this plugin, build the JAR with `./gradlew :tools:plugins:network:build`, move the JAR to
> `corda-cli-plugin-host/build/plugins/` and run the commands as shown below by locating corda-cli.sh in
> `corda-cli-plugin-host/build/generatedScripts/`.

# Get Members List

> Use `--help` to see information about commands and available options.

This is a sub-command under the `network` plugin to view the member list via HTTP.

For example,

```shell
./corda-cli.sh network --user=admin --password=admin --target=https://localhost:8888 members-list -h=<holding-identity-short-hash>
```
