# Corda CLI Plugin Host

## Setup/Build

run `./gradlew build`

* This will produce:
  * one jar, named corda-cli.jar, located in the `app/build/libs/` directory
  * two plugins zips located in `build/plugins` directory.
* The plugins are:
  * `plugin-example-plugin-one-0.0.1.zip`
  * `plugin-example-plugin-two-0.0.1.zip`

## Running The CLI Script

As part of the build process scripts are generated in the 'build/generatedScripts' directory. This ensures scripts will always refer to the correct version of the corda-cli.jar. Running './gradlew build' will trigger copying of scripts from the root 'scripts' directory to 'build/generatedScripts' and update the version referenced in the scripts accordingly, along with generation of the needed Jars. You may also manually trigger this task with './gradlew generateVersionedScripts' if required, but the corda-cli jar must be generated and present in the 'app\build\libs' to execute these scripts.

In the build/generatedScripts directory there is a windows cmd shell command script that can be called after a gradlew
Build. `corda-cli.cmd` etc

## The Plugins

### Example Plugin One

Root Command: `pluginOne`
Sub Commands included:

1. `basicExample` - Prints a welcome message.
2. `serviceExample` - Uses and injected service.

### Example Plugin Two

Root Command: `pluginTwo`
Sub Commands included:

1. `subCommand` - Prints a welcome message.

## Writing Your Own Plugin

### Plugin Location

Before you begin please see the [plugin guidelines doc](PluginGuidelines.md)

The easiest place to create a plugin is in this project in the 'plugins' module. If you do not wish to create one here,
you will have to ensure that you have gradle tasks that bundle the plugin for pf4j, more
info [here](https://pf4j.org/doc/packaging.html)

### Basics

To Write your own plugin for the CLI you must depend on this project's 'api' module.

The API module contains the `CordaCliPlugin` Interface which must be used when constructing your plugin. For examples of
use please see the 'plugins' module where you will find two example plugins.

> **NOTE:** _**When importing the CordaCliPlugin API module in the `build.gradle` you must use `compileOnly` to avoid classpath clashes.**_

To construct a plugin you will have to follow the pf4j pattern below:

```kotlin
class ExamplePlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    override fun start() {
    }

    override fun stop() {
    }

    @Extension
    @CommandLine.Command(name = "example")
    class ExamplePlugin : CordaCliPlugin
}
```

In the above example, your wrapper is passed the plugin wrapper by pf4j on plugin load, and must implement the Plugin
Interface. You must override `start()` and `stop()` and place any bootstrapping or teardown logic your plugin will need
here.

`@Extension` must be placed on the root picocli command of your plugin, and you must implement `CordaCliPlugin`.

In your plugins `gradle.properties` file you must put the following plugin metadata

```properties
version=<plugin version>
pluginId=<unique plugin name>
pluginClass=<package of your plugin wrapper e.g. net.corda.cli.plugins.myplugin.MyPlugginWrapper>
pluginProvider=R3
pluginDescription=<A brief description>
```

### Services

If you wish to use a service supplied by the plugin host (currently only HttpRpcService) you must implement
a `ServiceUser` interface for each service you will use. This interface looks similar to the following:

```kotlin
interface HttpServiceUser : ServiceUser {
    var service: HttpService
}
```

And its implementation requires a `lateinit` keyword like below:

```kotlin
@Extension
@CommandLine.Command(name = "plugin", description = ["Example Plugin using services"])
class ExamplePlugin : CordaCliPlugin, HttpServiceUser {
    override lateinit var service: HttpService

    @CommandLine.Command(
        name = "serviceExample",
        description = ["A subcommand that uses a service supplied by the host."]
    )
    fun exampleServiceSubCommand() {
        println(service.get())
    }
}
```

## Things to look out for / Debugging

### Multiple class bindings found

Please ensure that the plugins are using `compileOnly` for any dependency that is already set as implementation on the
host.

### Liquibase & other libraries that use context class paths

If you are creating a plugin using liquibase, you will have to manually set the context class path in the plugin to use
its own class path, and not the hosts. This can be done by setting it in the plugins start method like this:

```kotlin
override fun start() {
    Thread.currentThread().contextClassLoader = this::class.java.classLoader
}
```

This will also work for any plugin that requires its own class path be used as the context, instead of the hosts.

### Plugin not showing up

There are a few things that can cause this silent error. The main reason for this is a missing `@Extension` annotation
on the plugin's entry point class.

Another is forgetting to add the `CordaCliPlugin` interface to the entry point.

Another is mismatched plugin metadata. The pluginClass set in the properties file must match the pf4j wrapper class that loads the plugin.

### Running a remote debugger

In order to debug the plug-in host, you need to use Java remote debugging. Start by creating a standard remote debug
config via intellij's run/debug configuration manager. This will provide you with an agent setting that needs to be
added to the process's command line. 

In order to simplify this, a debug version of the CLI start script is provided that
uses the standard IntelliJ remote debugging settings:

* script/corda-cli-debug.cmd
* script/corda-cli-debug.sh

* After invoking this script, start the remote debugger and it will connect. 