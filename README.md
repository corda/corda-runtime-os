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

In the script directory there is a windows cmd shell command script that can be called after a gradlew
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

###### _**PLEASE NOTE: When importing the CordaCliPlugin API module, you must use compileOnly**_

To construct a plugin you will have to follow the pf4j pattern below:

```kotlin
class ExamplePluginWrapper(wrapper: PluginWrapper) : Plugin(wrapper) {

    override fun start() {
    }

    override fun stop() {
    }

    @Extension
    @CommandLine.Command(name = "example")
    class ExamplePlugin : CordaCliPlugin {}
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
interface HttpServiceUser: ServiceUser {
    var service: HttpService
}
```

And its implementation requires a `lateinit` keyword like below: 

```kotlin
@Extension
@CommandLine.Command(name = "plugin", description = ["Example Plugin using services"])
class ExamplePlugin : CordaCliPlugin, HttpServiceUser {
    override lateinit var service: HttpService
    
    @CommandLine.Command(name = "serviceExample", description = ["A subcommand that uses a service supplied by the host."])
    fun exampleServiceSubCommand() {
        println(service.get())
    }
}
```
