# Corda CLI Plugin Host

## Setup/Build

run `./gradlew build`

* This will produce:
  * one jar, named corda-cli.jar, located in the `app/build/libs/` directory
  * an example plugin located in `build/plugins` directory.
* The example plugin is:
  * `plugin-example-example-plugin-0.0.1.jar`

## Running The CLI Script

As part of the build process scripts are generated in the 'build/generatedScripts' directory. This ensures scripts will always refer to the correct version of the corda-cli.jar. Running './gradlew build' will trigger copying of scripts from the root 'scripts' directory to 'build/generatedScripts' and update the version referenced in the scripts accordingly, along with generation of the needed Jars. You may also manually trigger this task with './gradlew generateVersionedScripts' if required, but the corda-cli jar must be generated and present in the 'app\build\libs' to execute these scripts.

In the build/generatedScripts directory there is a windows cmd and shell command script that can be called after a gradlew Build. `corda-cli.cmd` etc

## The Plugins

### Example Plugin One

Root Command: `example-plugin`
Sub Commands included:

1. `sub-command` - Prints a welcome message.

## Config

### Logging config

Corda CLI will log everything to a file in the users home directory located in `~/.corda/cli/logs` by default, this behaviour can be changed by editing the corda-cli.cmd/sh files.
There are two options available to users.
- `-DlogLevel` - The minimum level you wish to be logged  
- `-DlogFile` - Where you want the log file to be located

you can add these flags as Java Parameters before the jar file is called. 

### Plugin Config

You can also change the plugin directory by editing these files. 
- `-Dpf4j.pluginsDir` will change the directory plugins are loaded from
