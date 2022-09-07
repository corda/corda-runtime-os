# Corda CLI Plugin Host

## Setup/Build

Run `./gradlew build`

* This will produce:
  * one jar, named `corda-cli.jar`, located in the `app/build/libs/` directory
  * one plugin ZIP, named  `plugin-example-plugin-0.0.1.zip` plugin zips in `build/plugins` directory
  * wrapper scripts, including `corda-cli.sh` in `build/generatedScripts`
  
So far, there's basically nothing very useful, all you have is way to run plugins.
To get useful functionality, you will need to then go and build plugins from other repositories, and 
copy them in to `build/plugins`. Plugin JARs can be placed into `build/plugins` without rebuilding this plugin host project; they will be picked up dynamically, and
you do not have to list them out or do anything more than just copy them in to this directory. To work with Corda 5 you will need
[corda-runtime-os/tools/plugins/package](https://github.com/corda/corda-runtime-os/tree/release/os/5.0/tools/plugins/package) and
[corda-runtime-os/tools/plugins/mgm](https://github.com/corda/corda-runtime-os/tree/release/os/5.0/tools/plugins/mgm), so execute:

```
(cd .. && git clone https://github.com/corda/corda-runtime-os.git)
(cd ../corda-runtime-os && ./gradlew :tools:plugins:package:build  :tools:plugins:mgm:build)
cp ../corda-runtime-os/tools/plugins/package/build/libs/package-cli-plugin-*.jar ../corda-runtime-os/tools/plugins/mgm/build/libs/mgm-cli*.jar build/plugins/
```

## Running The CLI Script

As part of the build process scripts are generated in the 'build/generatedScripts' directory. This ensures scripts will always refer to the correct version of the corda-cli.jar. Running './gradlew build' will trigger copying of scripts from the root 'scripts' directory to 'build/generatedScripts' and update the version referenced in the scripts accordingly, along with generation of the needed Jars. You may also manually trigger this task with './gradlew generateVersionedScripts' if required, but the corda-cli jar must be generated and present in the 'app\build\libs' to execute these scripts.

In the build/generatedScripts directory there is a windows cmd and shell command script that can be called after a gradlew Build. `corda-cli.cmd` etc

## The Plugins

Please refer to the detailed documemntation for each plugin:

* [mgm plugin README.md](https://github.com/corda/corda-runtime-os/tree/release/os/5.0/tools/plugins/mgm) for generating group policy files which are required to make CPIs, which are required to run a CorDapp. 
* [package plugin README.md](https://github.com/corda/corda-runtime-os/tree/release/os/5.0/tools/plugins/package) for generating CPB and CPI files

### Example Plugin

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
