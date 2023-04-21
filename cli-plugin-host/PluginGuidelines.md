# Corda CLI Host - Developer Guidelines for Writing Plugins

## Introduction

Corda CLI is built using the [pf4j](https://pf4j.org/) plugin system. 
This allows developers to independently develop a plugin tailored for their needs without the risk of polluting the CLI with excess commands. 

There are a few guidelines that must be followed when writing a plugin for the plugin module in corda-runtime-os.

## Plugin Location

Develop plugins in the [corda-runtime-os](https://github.com/corda/corda-runtime-os) repository under the `tools:plugins:` module.
Under this module create a new module for your plugin, and add it to `settings.gradle` in the project root.


## Starting With Gradle
The new module should have a new `build.gradle` file. First, create a plugin block and import CLI plugin packager as follows: 

```groovy
plugins{
    id 'corda.cli-plugin-packager'
}
```

This allows the plugin to be compiled into a special fat jar that deals with certain dependency issues and allows the use of `cliPlugin` configuration to configure the plugin. This should be placed after dependencies.
The following is an example configuration:

```groovy
cliPlugin{
    cliPluginClass = 'net.corda.cli.plugins.example.ExamplePlugin'
    cliPluginDescription = 'An Example Plugin'
}
```
Where:
- `cliPluginClass` - the main class of the plugin. See [Basic plugin structure](#basic-plugin-structure).
- `cliPluginDescription` - a brief description of the plugin.

The following options are also available:
- `cliPluginProvider` - the publisher of the plugin. This defaults to 'R3 Ltd.' but may be changed.
- `cliPluginId` - the unique ID your plugin must have. This defaults to the plugins package name so does not need to be set. However, you can change it if you need to.

### Required dependencies

In the `dependencies` block, at a minimum, import the following:

```groovy
dependencies {
    compileOnly "org.pf4j:pf4j:$pf4jVersion"
    compileOnly "net.corda.cli.host:api:$pluginHostVersion"

    kapt "org.pf4j:pf4j:$pf4jVersion"
    kapt "info.picocli:picocli:$picocliVersion"

    testImplementation "org.pf4j:pf4j:$pf4jVersion"
    testCompileOnly "net.corda.cli.host:api:$pluginHostVersion"
}
```

- `org.f4j:pf4j` — you need a compile only version of pf4j as the host supplies the classes at runtime.
- `net.corda.cli.host:api` — contains the interfaces needed for the host to load your plugin.
- `info.picocli:picocli` — the command line library used in the host.

## Basic plugin structure
To create the skeleton of your plugin, create the main class like the following: 

```kotlin
package net.corda.cli.plugins.example

import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.plugins.vnode.commands.ResetCommand
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class ExamplePlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun start() {
        logger.info("starting Example plugin")
    }

    override fun stop() {
        logger.info("stopping Example plugin")
    }

    @Extension
    @CommandLine.Command(name = "example", description = ["An Example Plugin."])
    class PluginEntryPoint : CordaCliPlugin
}
```

Let's examine this class in more detail:

```kotlin
class ExamplePlugin(wrapper: PluginWrapper) : Plugin(wrapper) 
```
This is the main class, listed in the `cliPlugin` gradle configuration.
It must inherit a pf4j `Plugin` class, which takes a `PluginWrapper`. The host supplies the wrapper, just ensure you inherit `Plugin`.

```kotlin
 override fun start() 
 
 ........
    
 override fun stop() 

```

These two functions allow setup and teardown of anything that might be needed when the plugin is starting or closing.
An example of this is setting the classloader for the plugin

```kotlin
@Extension
@CommandLine.Command(name = "example", description = ["An Example Plugin."])
class PluginEntryPoint : CordaCliPlugin
```

Finally we come to the plugin entry point, this is the class that is picked up and loaded into the host as the plugins main code.
- `@Extension` — allows pf4j to identify this class as an extension point of the plugin and load it to the host.
- `@CommandLine.Command` - indicates to picocli that this class is a command line command, called 'example' in this case.
- `class PluginEntryPoint : CordaCliPlugin` - CordaCliPlugin is needed for the host to identify that this is the plugin.

## Subcommands

Subcommands can be created as standard in picocli. You can find more information [https://picocli.info/#_introduction](here).

## Output and Logging

By default, `system out` and `system err` are captured by the logging framework of the host and logged.
These loggers are named `SystemOut` and `SystemErr`. Should you need to access them they can be retrieved as follows:

```kotlin
val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
val errOut: Logger = LoggerFactory.getLogger("SystemErr")
```

These are special loggers, which should only log `info` and `error` respectively. You may also log to them by calling any system print call. 
They appear as regular SYSTEM_OUT on the terminal and are logged as [System Out] and [System Err] in the log file logs.

To log any other information that the user will not see in the terminal, use the class's logger as normal. For example:

```kotlin
LoggerFactory.getLogger(this.javaClass)
```

**Note:** Do not redirect `system.out` or `system.err` in your plugin.

## Dependencies

As the CLI is a plugin system, the dependencies can cause issues such as clashes or missing classes.
There are several libraries that are included in the host that are stripped from the plugins at compile time.

You can find a list of these in the `cli-plugin-packager` gradle plugin located in `buildSrc` of `corda-runtime-os`.

If you experience strange class issues, such as the following, run `gradlew dependency --configuration runtimeClasspath` on the plugin and the host:

- `cannot cast sun.proxy12<*> to <class>`
- `<class> not found`
- other unusual class related runtime issues

If there is a library on both, set it to compile time on the plugin.

## Code standards

The plugins must be kept to a high and consistent code standard and must be reviewed by a member of the CLI team.

### Command syntax 
Commands must be in the format `<noun> <verb>`. For example:

`corda-cli vnode reset`

Flags must be sensible and consistent. For example, you should not have a flag of say `-p` for something like a URL.

Some suggestions:
- `-t` - target url
- `-i` OR `-f` - input file
- `-o` - output file
- `-u` - user
- `-p` - password

### Help 

Your plugin should also provide comprehensive help. You can find more information [here](https://picocli.info/quick-guide.html#_help_options)

