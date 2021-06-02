## Flow Worker Convention Plugins

These plugins are provided as a way to assist and ease the development of libraries and applications using the
flow-worker framework.

## Plugins

### Common App Plugin

The **Common App** plugin specifies the set of Gradle tasks, the common configurations and dependencies to build a
self-sufficient bootable JAR from the module of the application. The built bootable JAR is the form application is
distributed.

The bootable JAR embeds the [Apache Felix](https://felix.apache.org/)
[OSGi Release 7](http://docs.osgi.org/specification/osgi.core/7.0.0/ch01.html) framework, and the OSGi bundles the
application needs to run. At runtime, the bootable JAR starts Felix and activates the bundles zipped inside itself.

The **Common App** plugin depends on the code in the `osgi-framework-bootstrap` module to bootstrap and control the
Felix framework, to activate bundles and to stop the application at runtime.

#### How to apply the plugin

To build a bootable jat from the application module, apply the plugin in the `build.gradle`
of application module with

```groovy
plugins {
    id 'corda.publish'
    id 'corda.common-app'
}
```

#### How to declare application's dependencies.

The dependency management configurations defined by the Gradle
[**Java**](https://docs.gradle.org/current/userguide/java_plugin.htm) plugin and the [**Java
Library**](https://docs.gradle.org/current/userguide/java_library_plugin.html)
plugin are available to develop using the **Common App* plugin.

* For the application bundle itself, **declare dependencies as usual** (i.e. `implementation` and `api`).


* For bundles that the application needs **at runtime**, but not to compile the current project bundle,
  the `runtimeOnly` configuration will cause the dependency to be picked up and packaged into the application bootable
  JAR.


* If dependencies contain packages that should be **exposed inside the OSGi framework, but not installed as bundles
  themselves**, these dependencies should be listed under a special configuration called `systemPackages` provided by
  this plugin.
    * *IMPORTANT! This configuration should only be used when necessary.*


* If a module uses **OSGi components** of the framework, declare the dependencies on OSGi libraries in
  module's `build.gradle` as

```groovy
dependencies {
    compileOnly "org.osgi:osgi.annotation:$osgiVersion"
    compileOnly "org.osgi:osgi.cmpn:$osgiVersion"
    compileOnly "org.osgi:osgi.core:$osgiVersion"
}
```

Felix, embedded in the bootable JAR, exports the `org.osgi` packages to the bundles it activates.

#### How to build an application

To build the bootable JAR, run the Gradle task

* `appJar` - builds the application module as OSGI bundle. It zips those dependencies are OSGi bundles and the bundle of
  the application itself in the bootable JAR. It defines the bootable JAR manifest to bootstrap Felix and activates all
  the bundles zipped.

#### Logging

The plugin and the bootable JAR exposes the [SLF4J](http://www.slf4j.org/) and
[Apache Log4j 2](https://logging.apache.org/log4j/2.x/) packages to the bundles through the OSGi framework, using *Log4j
2* to implement logging.

In *Kotlin* code declare the `logger` as usual.

```kotlin
val logger = LoggerFactory.getLogger(MyClass::class.java)
```

The application logs in a single flow according the time of generations all events, either logged by OSGi bundles, Felix
or the code bootstrapping and controlling Felix.

#### How to define the entry point of an application

Just one class must implement the `net.corda.osgi.api.Application` interface. This class must be an OSGi component to
register itself as an OSGi service.

Annotate the class implementing `net.corda.osgi.api.Application` with

```kotlin
@Component(immediate = true)
```

See [OSGi Core r7 5.2.2 Service Interface](http://docs.osgi.org/specification/osgi.core/7.0.0/framework.service.html).

The `net.corda.osgi.api.Application.startup(args: Array<String>)` is the entry-point of the application called when all
the OSGi bundles zipped in the bootable JAR are active.

If no class implements the `net.corda.osgi.api.Application` interface, the application bootstrap throws an exception and
stops.

See [How to stop the application programmatically] for a full example of how to
implement `net.corda.osgi.api.Application`

#### How to clean-up and release resources before to quit

The `net.corda.osgi.api.Application.shutdown()` method is called before the OSGi framework stops. Code here the
behaviour of the application to release resources and to clean-up before to stop.

#### How to stop the application programmatically

```kotlin
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(immediate = true)
class App : Application {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(App::class.java)
    }

    override fun startup(args: Array<String>) {
        logger.info("START-UP")
        Thread.sleep(1000)
        Thread {
            shutdownOSGiFramework()
        }.start()
    }

    override fun shutdown() {
        logger.info("SHUTDOWN")
    }

    private fun shutdownOSGiFramework() {
        val bundleContext: BundleContext? = FrameworkUtil.getBundle(GoodbyeWorld::class.java).bundleContext
        if (bundleContext != null) {
            val shutdownServiceReference: ServiceReference<Shutdown>? =
                bundleContext.getServiceReference(Shutdown::class.java)
            if (shutdownServiceReference != null) {
                bundleContext.getService(shutdownServiceReference)?.shutdown(bundleContext.bundle)
            }
        }
    }

}
```

#### Testing

See [OSGi Test Conventions Plugin](#OSGi Test Conventions Plugin).

**

#### Advanced dependency declarations and Gradle tasks

The **Common App** plugins allows developers to use the

To build a module as an OSGi bundle, the application depends on, use the [Common Libry Plugin](#Common Library Plugin)
.rara## Flow Worker Convention Plugins

These plugins are provided as a way to assist and ease the development of libraries and applications using the
flow-worker framework.

## Plugins

### Common App Plugin

The **Common App** plugin specifies the set of Gradle tasks, the common configurations and dependencies to build a
self-sufficient bootable JAR from the module of the application. The built bootable JAR is the form application is
distributed.

The bootable JAR embeds the [Apache Felix](https://felix.apache.org/)
[OSGi Release 7](http://docs.osgi.org/specification/osgi.core/7.0.0/ch01.html) framework, and the OSGi bundles the
application needs to run. At runtime, the bootable JAR starts Felix and activates the bundles zipped inside itself.

The **Common App** plugin depends on the code in the `osgi-framework-bootstrap` module to bootstrap and control the
Felix framework, to activate bundles and to stop the application at runtime.

#### How to apply the plugin

To build a bootable jat from the application module, apply the plugin in the `build.gradle`
of application module with

```groovy
plugins {
    id 'corda.publish'
    id 'corda.common-app'
}
```

#### How to declare application's dependencies.

The dependency management configurations defined by the Gradle
[**Java**](https://docs.gradle.org/current/userguide/java_plugin.htm) plugin and the [**Java
Library**](https://docs.gradle.org/current/userguide/java_library_plugin.html)
plugin are available to develop using the **Common App* plugin.

* For the application bundle itself, **declare dependencies as usual** (i.e. `implementation` and `api`).


* For bundles that the application needs **at runtime**, but not to compile the current project bundle,
  the `runtimeOnly` configuration will cause the dependency to be picked up and packaged into the application bootable
  JAR.


* If dependencies contain packages that should be **exposed inside the OSGi framework, but not installed as bundles
  themselves**, these dependencies should be listed under a special configuration called `systemPackages` provided by
  this plugin.
    * *IMPORTANT! This configuration should only be used when necessary.*


* If a module uses **OSGi components** of the framework, declare the dependencies on OSGi libraries in
  module's `build.gradle` as

```groovy
dependencies {
    compileOnly "org.osgi:osgi.annotation:$osgiVersion"
    compileOnly "org.osgi:osgi.cmpn:$osgiVersion"
    compileOnly "org.osgi:osgi.core:$osgiVersion"
}
```

Felix, embedded in the bootable JAR, exports the `org.osgi` packages to the bundles it activates.

#### How to build an application

To build the bootable JAR, run the Gradle task

* `appJar` - builds the application module as OSGI bundle. It zips those dependencies are OSGi bundles and the bundle of
  the application itself in the bootable JAR. It defines the bootable JAR manifest to bootstrap Felix and activates all
  the bundles zipped.

#### Logging

The root `build.gradle`  declares the logger dependencies as...

```gradle
dependencies {    
    implementation "org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion"
}
```

This plugin and the bootable JAR exposes the [SLF4J](http://www.slf4j.org/) and
[Apache Log4j 2](https://logging.apache.org/log4j/2.x/) packages to the bundles through the OSGi framework, using *Log4j
2* to implement logging, hence this plugin doesn't zip the SLF4J and Log4j in the bootable JAR.

In *Kotlin* code declare the `logger` as usual.

```kotlin
val logger = LoggerFactory.getLogger(MyClass::class.java)
```

The application logs in a single flow according the time of generations all events, either logged by OSGi bundles, Felix
or the code bootstrapping and controlling Felix.

#### Command Line Interface arguments handling.

The code bootstrapping Felix exposes the Command Line Interface arguments as an OSGi service to the bundles zipped in
the bootable JAR built by this plugin and activated at runtime.

To get the arguments from code of an OSGi bundles in *Kotlin*, use...

```kotlin
val serviceReference = bundleContext.getServiceReference(ArgsService::class.java)
val argsService = bundleContext.getService(serviceReference)
val args = argsService.getArgs()
```

See [OSGi Core r7 5.2.2 Service Interface](http://docs.osgi.org/specification/osgi.core/7.0.0/framework.service.html).

#### Testing

See [OSGi Test Conventions Plugin](#OSGi Test Conventions Plugin).

*IMPORTANT! After the `clean` or `osgi-framework-bootstrap:clean` tasks, the
task `osgi-framework-bootstrap:framework-app-tester:appJar` must run before any `test` or `build` task
because the unit tests for the `osgi-framework-bootstrap` module need to try to activate at the OSGi bundle
of the application.
The `osgi-framework-bootstrap:framework-app-tester:appJar` task builds a dummy application bundle.

#### Advanced dependency declarations and Gradle tasks

The **Common App** plugins allows developers to use the

To build a module as an OSGi bundle, the application depends on, use the [Common Libry Plugin](#Common Library Plugin)
.rara

The plugin defines two additional configurations to declare dependencies:

* `bootstrapClasspath` - zips packages in the bootable JAR and exposes them to the runtime class-path of the process
  bootstrapping Felix.
    * **NOTE! Zipped packages are not exported through the OSGi framework to activated bundles!**
    * *WARNING! This configuration is provided for internal use of the developers of the Common App plugin:
      it shouldn't be used to develop applications, and it's described here for sake of completeness of the
      documentation.*


* `systemPackages` - zips packages in the bootable JAR and exposes them to the runtime class-path of the process
  bootstrapping Felix.
    * **NOTE! Zipped packages are exported through the OSGi framework to activated bundles!**
    * *IMPORTANT! Dependencies containing packages that should be exposed inside the OSGi framework, but not installed
      as bundles themselves should be declared as `systemPackages`. This configuration should only be used when
      necessary.*
    * *IMPORTANT! Runtime dependencies - as `implementation` colliding with dependencies defined in `systemPackages`
      are not zipped as OSGi bundles in the bootable application JAR, regardless the version. What declared as
      `systemPackages` has precedence.*

The `appJar` task depends on the tasks

* `cordaAssembleSystemPackagesExtra` - to list in the file `system_packages_extra`
  the OSGi *Export-Package* instructions needed to export the dependencies declared with `systemPackages` through the
  OSGi framework to the bundles. The file `system_packages_extra` is created at `build/resources/main/` path relative to
  the module. The `systemPackages` dependencies are zipped in the bootable JAR by the `appJar` task. This task depends
  on...


* `cordaAssembleBundles` - copies in the `bundles` directory the OSGi dependencies, and it lists them in the
  file `system_bundles`. The `bundles` directory and `system_bundles` file are assembled in the
  `build/resources/main/` path relative to the module.

#### The bootable JAR

The bootable JAR contains all project class files and resources packed together with all it's dependencies.

The OSGi bundles are embedded in the directory `bundles`, which is a child of the root classpath.

The file `system_bundles` in the root of the classpath lists the paths to access the bundles to activate.

The file `system_packages_extra` in the root of the classpath lists packages exposed from this classpath to the bundles
active in the OSGi framework.

The classpath or executable JAR has the following structure.

```
  <root_of_classpath>
  +--- bundles
  |    +--- <bundle_1.JAR>
  |    +--- <...>
  |    +--- <bundle_n.JAR>
  +--- system_bundles
  \___ system_packages_extra
```

### Common Library Plugin

The **Common Library** plugin will specify the set of common configurations and dependencies that a library developer
will need. More can of course be added but, as an example, the minimum needed gradle for a library (when applying the
plugin)
would be

```groovy
plugins {
    id 'corda.common-library'
    id 'corda.publish'
}

description 'Hello World Implementation'
```

The tasks are as follows:

* `JAR` - compiles the library and packages it into a JAR with the OSGi manifest defined as:

```groovy
bnd """
    Bundle-Name: $project.description
    Bundle-SymbolicName: ${project.group}.${project.name}
"""
```

### OSGi Test Conventions Plugin

The **OSGi Test Conventions** plugin provides tasks useful for running OSGi integration tests as well as some necessary
dependencies.

The tasks are as follows:

* `integrationTest` - executes the integration tests within an OSGi framework.

* `testingBundle` - compiles integration tests into an OSGi bundle which can be loaded.

* `resolve` - resolves all bundle dependencies and verifies them against the `test.bndrun` file.

* `testOSGi` - executes the integration tests within an OSGi framework.

* `check` - executes the integration tests within an OSGi framework.

### Publish Plugin

The **Publish** plugin provides the information to correctly publish artifacts to the R3 servers.

No specific tasks are defined by this plugin.

The plugin defines two additional configurations to declare dependencies:

* `bootstrapClasspath` - zips packages in the bootable JAR and exposes them to the runtime class-path of the process
  bootstrapping Felix.
    * **NOTE! Zipped packages are not exported through the OSGi framework to activated bundles!**
    * *WARNING! This configuration is provided for internal use of the developers of the Common App plugin:
      it shouldn't be used to develop applications, and it's described here for sake of completeness of the
      documentation.*


* `systemPackages` - zips packages in the bootable JAR and exposes them to the runtime class-path of the process
  bootstrapping Felix.
    * **NOTE! Zipped packages are exported through the OSGi framework to activated bundles!**
    * *IMPORTANT! Dependencies containing packages that should be exposed inside the OSGi framework, but not installed
      as bundles themselves should be declared as `systemPackages`. This configuration should only be used when
      necessary.*
    * *IMPORTANT! Runtime dependencies - as `implementation` colliding with dependencies defined in `systemPackages`
      are not zipped as OSGi bundles in the bootable application JAR, regardless the version. What declared as
      `systemPackages` has precedence.*

The `appJar` task depends on the tasks

* `cordaAssembleSystemPackagesExtra` - to list in the file `system_packages_extra`
  the OSGi *Export-Package* instructions needed to export the dependencies declared with `systemPackages` through the
  OSGi framework to the bundles. The file `system_packages_extra` is created at `build/resources/main/` path relative to
  the module. The `systemPackages` dependencies are zipped in the bootable JAR by the `appJar` task. This task depends
  on...


* `cordaAssembleBundles` - copies in the `bundles` directory the OSGi dependencies, and it lists them in the
  file `system_bundles`. The `bundles` directory and `system_bundles` file are assembled in the
  `build/resources/main/` path relative to the module.

#### The bootable JAR

The bootable JAR contains all project class files and resources packed together with all it's dependencies.

The OSGi bundles are embedded in the directory `bundles`, which is a child of the root classpath.

The file `system_bundles` in the root of the classpath lists the paths to access the bundles to activate.

The file `system_packages_extra` in the root of the classpath lists packages exposed from this classpath to the bundles
active in the OSGi framework.

The classpath or executable JAR has the following structure.

```
  <root_of_classpath>
  +--- bundles
  |    +--- <bundle_1.JAR>
  |    +--- <...>
  |    +--- <bundle_n.JAR>
  +--- system_bundles
  \___ system_packages_extra
```

### Common Library Plugin

The **Common Library** plugin will specify the set of common configurations and dependencies that a library developer
will need. More can of course be added but, as an example, the minimum needed gradle for a library (when applying the
plugin)
would be

```groovy
plugins {
    id 'corda.common-library'
    id 'corda.publish'
}

description 'Hello World Implementation'
```

The tasks are as follows:

* `JAR` - compiles the library and packages it into a JAR with the OSGi manifest defined as:

```groovy
bnd """
    Bundle-Name: $project.description
    Bundle-SymbolicName: ${project.group}.${project.name}
"""
```

### OSGi Test Conventions Plugin

The **OSGi Test Conventions** plugin provides tasks useful for running OSGi integration tests as well as some necessary
dependencies.

The tasks are as follows:

* `integrationTest` - executes the integration tests within an OSGi framework.

* `testingBundle` - compiles integration tests into an OSGi bundle which can be loaded.

* `resolve` - resolves all bundle dependencies and verifies them against the `test.bndrun` file.

* `testOSGi` - executes the integration tests within an OSGi framework.

* `check` - executes the integration tests within an OSGi framework.

### Publish Plugin

The **Publish** plugin provides the information to correctly publish artifacts to the R3 servers.

No specific tasks are defined by this plugin.