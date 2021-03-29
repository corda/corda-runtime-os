## Flow Worker Convention Plugins

These plugins are provided as a way to assist and ease the development
of libraries and applications using the flow-worker framework.

## Plugins
### Common Library Plugin

The Common Library plugin will specify the set of common configurations and dependencies
that a library developer will need.  More can of course be added but, as an example, the
minimum needed gradle for a library (when applying the plugin) would be

```groovy
plugins {
    id 'corda.common-library'
    id 'corda.publish'
}

description 'Hello World Implementation'
```

The tasks are as follows:

jar - compiles the library and packages it into a jar with the OSGi manifest defined as:

```groovy
bnd """
    Bundle-Name: $project.description
    Bundle-SymbolicName: ${project.group}.${project.name}
"""
```

### OSGi Test Conventions Plugin

The OSGi Test Conventions plugin provides tasks useful for running OSGi integration tests
as well as some necessary dependencies.

The tasks are as follows:

integrationTest - executes the integration tests within an OSGi framework.

testingBundle - compiles integration tests into an OSGi bundle which can be loaded. 

resolve - resolves all bundle dependencies and verifies them against the `test.bndrun` file.

testOSGi - executes the integration tests within an OSGi framework.

check - executes the integration tests within an OSGi framework.

### Publish Plugin

The Publish plugin provides the information to correctly publish artifacts to the R3
servers.

No specific tasks are defined by this plugin.