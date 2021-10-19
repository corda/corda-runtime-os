# Corda CLI PF4J And PicoCLI Demo

## Setup/Build

run `./gradlew build`

* This will produce:
    * one jar, named corda-cli.jar, located in the `app/build/libs/` directory 
    * two plugins zips located in `build/plugins` directory.
* The plugins are:
  * `plugin-flow-plugin-0.0.1.zip`
  * `plugin-node-plugin-0.0.1.zip`

## Run the demo

1. Run 

```
 ./gradlew app:run
```

2. The demo's output should look similar to: (Please see `Boot#main()` for more details)
```
2021-10-19 16:32:38,522 INFO net.corda.cli.application.Boot - Plugin directory: C:\Workspace\pf4j-kotlin-demo\build\plugins
2021-10-19 16:32:38,545 INFO org.pf4j.DefaultPluginStatusProvider - Enabled plugins: []
2021-10-19 16:32:38,545 INFO org.pf4j.DefaultPluginStatusProvider - Disabled plugins: []
2021-10-19 16:32:38,548 INFO org.pf4j.DefaultPluginManager - PF4J version 0.0.0 in 'deployment' mode
2021-10-19 16:32:38,569 INFO org.pf4j.AbstractPluginManager - Plugin 'flow-plugin@0.0.1' resolved
2021-10-19 16:32:38,570 INFO org.pf4j.AbstractPluginManager - Plugin 'node-plugin@0.0.1' resolved
2021-10-19 16:32:38,570 INFO org.pf4j.AbstractPluginManager - Start plugin 'flow-plugin@0.0.1'
2021-10-19 16:32:38,576 INFO net.corda.cli.plugins.demoB.ExampleFlowPlugin - ExampleFlowPlugin.start()
2021-10-19 16:32:38,578 INFO net.corda.cli.plugins.demoB.ExampleFlowPlugin - EXAMPLEFLOWPLUGIN
2021-10-19 16:32:38,578 INFO org.pf4j.AbstractPluginManager - Start plugin 'node-plugin@0.0.1'
2021-10-19 16:32:38,580 INFO net.corda.cli.plugins.demoA.ExampleNodePlugin - ExampleNodePlugin.start()
2021-10-19 16:32:38,582 INFO net.corda.cli.plugins.demoA.ExampleNodePlugin - EXAMPLENODEPLUGIN
2021-10-19 16:32:38,610 INFO net.corda.cli.application.Boot - Found 2 extensions for extension point 'net.corda.cli.api.CordaCliCommand'
2021-10-19 16:32:38,675 INFO net.corda.cli.application.Boot - Adding subcommands from >>> ExampleFlowPlugin
2021-10-19 16:32:38,678 INFO net.corda.cli.application.Boot - Adding subcommands from >>> ExampleNodePlugin
2021-10-19 16:32:38,683 INFO net.corda.cli.application.Boot - Extensions added by plugin 'flow-plugin':
2021-10-19 16:32:38,684 INFO net.corda.cli.application.Boot -    net.corda.cli.plugins.demoB.ExampleFlowPlugin$WelcomeCordaCliCommand
2021-10-19 16:32:38,684 INFO net.corda.cli.application.Boot - Extensions added by plugin 'node-plugin':
2021-10-19 16:32:38,685 INFO net.corda.cli.application.Boot -    net.corda.cli.plugins.demoA.ExampleNodePlugin$WelcomeCordaCliCommand
Missing required subcommand
Usage: corda [COMMAND]
Commands:
  flow
  node
2021-10-19 16:32:38,723 INFO org.pf4j.AbstractPluginManager - Stop plugin 'node-plugin@0.0.1'
2021-10-19 16:32:38,724 INFO net.corda.cli.plugins.demoA.ExampleNodePlugin - ExampleNodePlugin.stop()
2021-10-19 16:32:38,724 INFO org.pf4j.AbstractPluginManager - Stop plugin 'flow-plugin@0.0.1'
2021-10-19 16:32:38,724 INFO net.corda.cli.plugins.demoB.ExampleFlowPlugin - ExampleFlowPlugin.stop()

```

## Running the JAR

Note that for manually running the application jar in `app/build/libs/corda-cli.jar` the property
pf4j.pluginsDir for example using the parameter `-Dpf4j.pluginsDir=<absolute path to plugins dir>` when running Java.

## The Plugins

### Node Plugin
Root Command: `node`
Sub Commands included:
1. `status`
2. `address`

### flow Plugin
Root Command: `flow`
Sub Commands included:
1. `listAvailable`
2. `startFlow`
