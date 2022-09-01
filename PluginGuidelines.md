# Corda CLI Host - Developer Guidelines for Writing Plugins

## Introduction

Corda CLI is built using a plugin system [pf4j](https://pf4j.org/). 
This allows developers to independently develop a plugin tailored for there needs without worrying about polluting the cli with excess commands. 

There are a few guidelines that must be followed when writing a plugin that is to be added to the plugin module in corda-runtime-os.

## Plugin Location

The best place to develop plugins is in the [corda-runtime-os](https://github.com/corda/corda-runtime-os) repository under the `tools:plugins:` module

Under this module please create a new module for your plugin, and add it to settings.gradle in the project root.


## Starting With Gradle
In the new module you should have a new `build.gradle` file. Start by setting up a few important parts of the plugin ecosystem.

first create a plugin block and import cli plugin packager like so: 

```groovy
plugins{
    id 'corda.cli-plugin-packager'
}
```

this will allow the plugin to be compiled into a special fat jar that deals with certain dependency issues and allows the use of `cliPlugin` configuration to configure the plugin. This should be placed after dependencies.
And example of the configuration can be seen here:

```groovy
cliPlugin{
    cliPluginClass = 'net.corda.cli.plugins.example.ExamplePlugin'
    cliPluginDescription = 'An Example Plugin'
}
```
Here's what this is doing:
- `cliPluginClass` - This is the main class of the plugin, we will discuss this later.
- `cliPluginDescription` - this is a brief description of the plugin.

Other Options you can use:
- `cliPluginProvider` - The publisher of the plugin, defaults to 'R3 Ltd.', may be changed if needed.
- `cliPluginId` - The unique ID your plugin must have, it will default to the plugins package name so does not need to be set. However, you can change it if you need to.

### The required dependencies

in the `dependencies` block you will need to import these at a minimum:

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

- `org.f4j:pf4j` - you will need to have a compile only version of pf4j as the host will supply the classes at runtime.
- `net.corda.cli.host:api` - this contains the interfaces needed to have your plugin loaded by the host.
- `info.picocli:picocli` - the command line library used in the host.

## Creating you basic plugin structure
Now its time to create the skeleton of your plugin, the main class

You wil have to create a class like the following: 

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

Let's break this down a bit:

```kotlin
class ExamplePlugin(wrapper: PluginWrapper) : Plugin(wrapper) 
```

This is out main class, the one we have listed in the `cliPlugin` gradle configuration.
As you can see it must inherit a pf4j `Plugin` class which takes a `PluginWrapper`. 
You don't need to be worried about the wrapper the host will supply it, just make sure you inherit `Plugin`.

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
- `@Extension` allows pf4j to identify this class as an extension point of the plugin and load it to the host.
- `@CommandLine.Command` - tells picocli that this class is a command line command called 'example'
- `class PluginEntryPoint : CordaCliPlugin` - CordaCliPlugin is needed for the host to identify that this is the plugin.

## Adding SubCommands

Sub commands can be created as the usually would be in picocli you can find more info [https://picocli.info/#_introduction](here)

### Presenting output to the user / Logging

By default all System out and system err is captured by the hosts logging framework and logged. 
These loggers are named `SystemOut` and `SystemErr`, should you need to access them they can be retrieved like this:

```kotlin
val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
val errOut: Logger = LoggerFactory.getLogger("SystemErr")
```

These are special loggers, which should only log `info` and `error` respectively. You may also log to them by calling any system print call. 
They appear as regular SYSTEM_OUT on the terminal and are logged as [System Out] and [System Err] in the log file logs.

If you wish to log any other information that the user will not see in the terminal please use the class's logger like normal. example:

```kotlin
LoggerFactory.getLogger(this.javaClass)
```

*NOTE: DO NO REDIRECT SYSTEM OUT OR ERROR IN YOUR PLUGIN*

## Dependencies

As the cli is a plugin system the dependencies can cause issues such as clashes or missing classes.
There are several libraries that are included in the host, and will be stripped from the plugins at compile time. 

You can find a list of these in the `cli-plugin-packager` gradle plugin located in `buildSrc` of runtime os.

IF you are running into strange class issues, such as:

- `cannot cast sun.proxy12<*> to <class>`
- `<class> not found`
- other unusual class related runtime issues

Then you will need to run a `gradlew dependency --configuration runtimeClasspath` on the plugin and the host, if you notice that there is a library on both, set it to compile time on the plugin.

## Code Standards

The plugins should be kept to a high and consistent code standard and will have to be reviewed by someone on the CLI team. 

### Command syntax 
`<noun> <verb>` syntax is to be used for commands, and example:

`corda-cli vnode reset`

#### Flags
Flags must be sensible and consistent, we should not have a flag of say `-p` for something like a url etc.

some suggestions:
- `-t` - target url
- `-i` OR `-f` - input file
- `-o` - output file
- `-u` - user
- `-p` - password

### Help 

Your plugin should also provide comprehensive help, read more [here](https://picocli.info/quick-guide.html#_help_options)

