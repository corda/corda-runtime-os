# Corda 5 CLI plugins

These plugins are versioned alongside the Corda implementation due to
them requiring code from either the `corda-runtime-os`, or `corda-api`
repos.

## What's here
* db-config: Generates the initial db schema(s) for the cluster to later be applied to the cluster
* initial-config: Generates the initial values to be inserted into the config schema after it's been set up to be 
  applied to the cluster
* secret-config: Generates encrypted secrets for use in the configs we set up for the cluster
* Corda CLI plugins: Plugins for Corda CLI Plugin Host e.g. package, network.

## Configuring Gradle to build plugins

First add `corda.cli-plugin-packager` to the `plugins` block

```groovy
plugins {
  id 'corda.cli-plugin-packager'
}
```

Next add a `cliPlugin` configuration:

```groovy
cliPlugin {
    cliPluginClass = 'net.corda.cli.commands.mypackage.MyClass'
    cliPluginDescription = 'Plugin for ...'
}
```

Finally add an `installDist` task:

```groovy
tasks.named("installDist") {
    dependsOn cliPlugin
    def homePath = System.properties['user.home']
    from cliPlugin
    into "$homePath/.corda/cli/plugins"
}
```

## Plugin Smoke Tests
Smoke tests in individual Corda CLI plugin directories under `pluginSmokeTest` are run against the Combined Worker, intended to be triggered manually during development. There is also a nightly Jenkins job that runs these tests on the release branch. In the future, it may be included as a PR-gate.
