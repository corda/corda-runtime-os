## Flow Worker Convention Plugins

These plugins are provided as a way to assist and ease the development of libraries and applications using the
flow-worker framework.

## Plugins

### Common App Plugin

The Common App plugin specifies the set of Gradle tasks, the common configurations and dependencies to build a
self-sufficient bootable jar from the module of the application. The built bootable jar is the form application is
distributed.

The bootable jar embeds the [Apache Felix](https://felix.apache.org/)
[OSGi Release 7](http://docs.osgi.org/specification/osgi.core/7.0.0/ch01.html) framework, and the OSGi bundles the
application needs to run. At runtime, the bootable jar starts Felix and activates the bundles zipped inside itself.

The Common App plugin depends on the code in the `osgi-framework-bootstrap` module to bootstrap and control
the Felix framework, to activate bundles and to stop the application at runtime.

#### How to build an application

To build a bootable jat from the application module, apply the plugin in the `build.gradle`
of application module with

```groovy
plugins {
    id 'corda.publish'
    id 'corda.common-app'
}
```

If a module uses OSGi, declare the dependencies on OSGi libraries in module's `build.gradle` as

```groovy
dependencies {
    compileOnly "org.osgi:osgi.annotation:$osgiVersion"
    compileOnly "org.osgi:osgi.cmpn:$osgiVersion"
    compileOnly "org.osgi:osgi.core:$osgiVersion"
}
```

Felix, embedded in the bootable jar, exports the `org.osgi` packages to the bundles it activates.

To build the bootable jar, run the Gradle task

* `appJar` - builds the application module as OSGI bundle. It zips those dependencies are OSGi bundles and the bundle of
  the application itself in the bootable jar. It defines the bootable jar manifest to bootstrap Felix and activates all
  the bundles zipped.

#### Logging

The plugin and the bootable jar exposes the [SLF4J](http://www.slf4j.org/) and
[Apache Log4j 2](https://logging.apache.org/log4j/2.x/) packages to the bundles through the OSGi framework, using *Log4j
2* to implement logging.

In *Kotlin* code declare the `logger` as usual.

```Kotlin
val logger = LoggerFactory.getLogger(MyClass::class.java)
```

The application logs in a single flow according the time of generations all events, either logged by OSGi bundles, Felix
or the code bootstrapping and controlling Felix.

#### Testing

See [OSGi Test Conventions Plugin](#OSGi Test Conventions Plugin).

#### Advanced dependency declarations and Gradle tasks

The Common App plugins allows developers to use the

To build a module as an OSGi bundle, the application depends on, use the [Common Library Plugin](#Common Library Plugin)
.

The plugin defines two additional configurations to declare dependencies:

* `bootstrapClasspath` - zips packages in the bootable jar and exposes them to the runtime class-path of the process
  bootstrapping Felix.
  **Zipped packages are not exported through the OSGi framework to activated bundles!**

* `systemPackages` - zips packages in the bootable jar and exposes them to the runtime class-path of the process
  bootstrapping Felix.
  **Zipped packages are exported through the OSGi framework to activated bundles!**

The `appJar` task depends on the tasks

* `cordaAssembleSystemPackagesExtra` - to list in the file `system_packages_extra`
  the OSGi *Export-Package* instructions needed to export the dependencies declared with `systemPackages` through the
  OSGi framework to the bundles.
  The file `system_packages_extra` is created at `build/resources/main/` path relative to
  the module. The `systemPackages` dependencies are zipped in the bootable jar by the `appJar` task. This task depends
  on...
* `cordaAssembleBundles` - copies in the `bundles` directory the OSGi dependencies, 
  and it lists them in the file `system_bundles`.
  The `bundles` directory and `system_bundles` file are assembled in the 
  `build/resources/main/` path relative to the module.

#### The bootable jar

The bootable jar contains all project class files and resources packed together with all it's dependencies.

The OSGi bundles are embedded in the directory `bundles`, which is a child of the root classpath.

The file `system_bundles` in the root of the classpath lists the paths to access the bundles to activate.

The file `system_packages_extra` in the root of the classpath lists packages exposed from this classpath to the
bundles active in the OSGi framework.

The classpath or executable jar has the following structure.
```
  <root_of_classpath>
  +--- bundles
  |    +--- <bundle_1.jar>
  |    +--- <...>
  |    +--- <bundle_n.jar>
  +--- system_bundles
  \___ system_packages_extra
```

### Common Library Plugin

The Common Library plugin will specify the set of common configurations and dependencies that a library developer will
need. More can of course be added but, as an example, the minimum needed gradle for a library (when applying the plugin)
would be

```groovy
plugins {
    id 'corda.common-library'
    id 'corda.publish'
}

description 'Hello World Implementation'
```

The tasks are as follows:

* `jar` - compiles the library and packages it into a jar with the OSGi manifest defined as:

```groovy
bnd """
    Bundle-Name: $project.description
    Bundle-SymbolicName: ${project.group}.${project.name}
"""
```

### OSGi Test Conventions Plugin

The OSGi Test Conventions plugin provides tasks useful for running OSGi integration tests as well as some necessary
dependencies.

The tasks are as follows:

* `integrationTest` - executes the integration tests within an OSGi framework.

* `testingBundle` - compiles integration tests into an OSGi bundle which can be loaded.

* `resolve` - resolves all bundle dependencies and verifies them against the `test.bndrun` file.

* `testOSGi` - executes the integration tests within an OSGi framework.

* `check` - executes the integration tests within an OSGi framework.

### Publish Plugin

The Publish plugin provides the information to correctly publish artifacts to the R3 servers.

No specific tasks are defined by this plugin.