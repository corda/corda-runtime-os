# Corda CLI Plugin Host

## Setup/Build

Run `./gradlew build`

* This creates the following:
  * `corda-cli.jar` in the `app/build/libs/` directory
  * `plugin-example-plugin-0.0.1.zip`in the `build/plugins` directory
  * wrapper scripts, including `corda-cli.sh` in the `build/generatedScripts` directory
  
These files enable you to run plugins. You can build plugins from other repositories and copy them in to the `build/plugins` directory. Plugin JARs can be placed into `build/plugins` without rebuilding this plugin host project; they will be picked up dynamically, and you do not have to list them out or do anything more than just copy them in to this directory. To work with Corda 5, execute the following to retrieve [corda-runtime-os/tools/plugins/package](https://github.com/corda/corda-runtime-os/tree/release/os/5.0/tools/plugins/package) and [corda-runtime-os/tools/plugins/mgm](https://github.com/corda/corda-runtime-os/tree/release/os/5.0/tools/plugins/mgm):

```
(cd .. && git clone https://github.com/corda/corda-runtime-os.git)
(cd ../corda-runtime-os && ./gradlew :tools:plugins:package:build  :tools:plugins:mgm:build)
cp ../corda-runtime-os/tools/plugins/package/build/libs/package-cli-plugin-*.jar ../corda-runtime-os/tools/plugins/mgm/build/libs/mgm-cli*.jar build/plugins/
```

## Running the CLI Script

The build process generates scripts in the `build/generatedScripts` directory. This ensures scripts always refer to the correct version of `corda-cli.jar`. The build process copies the scripts from the root `scripts` directory to `build/generatedScripts` and updates the version referenced in the scripts accordingly. It also generates the required Jars. You can also manually trigger this task with `./gradlew generateVersionedScripts` if required, but the corda-cli jar must be generated and present in the `app\build\libs` to execute these scripts.

The `build/generatedScripts` directory contains a windows cmd and shell command script that can be called after a gradlew Build. `corda-cli.cmd` etc

## Plugins

Refer to the detailed documemntation for each plugin:

* [mgm plugin README.md](https://github.com/corda/corda-runtime-os/tree/release/os/5.0/tools/plugins/mgm) for generating group policy files which are required to make CPIs, which are required to run a CorDapp. 
* [package plugin README.md](https://github.com/corda/corda-runtime-os/tree/release/os/5.0/tools/plugins/package) for generating CPB and CPI files

### Example Plugin

Root Command: `example-plugin`
Sub Commands included:

* `sub-command` - Prints a welcome message.

## Config

### Logging config

Corda CLI logs everything to a file in the users home directory located in `~/.corda/cli/logs` by default. This behaviour can be changed by editing the following in the `corda-cli.cmd/sh` files:
- `-DlogLevel` - the minimum level to be logged.
- `-DlogFile` - location of the log file.

You can add these flags as Java Parameters before the jar file is called. 

### Plugin Config

You can also change the plugin directory by editing the following in the corda-cli.cmd/sh files:
- `-Dpf4j.pluginsDir`— changes the directory plugins are loaded from.
