# Flow Worker Convention Plugins

These plugins are provided as a way to assist and ease the development of libraries and applications using the
runtime OS framework.


## Common App Plugin

The **Common App** plugin specifies the set of Gradle tasks, the common configurations and dependencies to build a
self-sufficient bootable JAR from the module of the application. The built bootable JAR is the form application is
distributed.

The bootable JAR embeds the [Apache Felix](https://felix.apache.org/)
[OSGi Release 7](http://docs.osgi.org/specification/osgi.core/7.0.0/ch01.html) framework, and the OSGi bundles the
application needs to run. At runtime, the bootable JAR starts Felix and activates the bundles zipped inside itself.

The **Common App** plugin depends on the code in the `osgi-framework-bootstrap` module to bootstrap and control the
Felix framework, to activate bundles and to stop the application at runtime.


### How to apply the plugin

To build a bootable jat from the application module, apply the plugin in the `build.gradle`
of application module with

```groovy
plugins {
    id 'corda.publish'
    id 'corda.common-app'
}
```

### Configure the plugin

The plugin can be configured using the provided `osgiRun` extension, 
you can configure one or more Java Agent that wil be launched at application startup an OSGi framework properties as follows

```
osgiRun {
    frameworkProperties = [
        "some.key" : "some value",
        "another.key" : "another value"
    ]
    agent("some.agent.Class1", "Arguments to agent Class1")
    agent("some.agent.Class1", "Arguments to agent Class2")
}
```

> :warning: **The jar containing the Java agent class must also be added to either the 
> `systemPackages` or `bootstrapClasspath` configurations** 
> Specifically, if the agent classes need to be visible from within the OSGi framework (as it is the case for the Quasar Java agent)
> the agent jar must be added to the `systemPackages`, otherwise to the `bootstrapClasspath` configuration


### How to declare application's dependencies.

The dependency management configurations defined by the Gradle
[**Java**](https://docs.gradle.org/current/userguide/java_plugin.htm) plugin and the [**Java
Library**](https://docs.gradle.org/current/userguide/java_library_plugin.html)
plugin are available to develop using the **Common App* plugin.

* For the project bundle itself, **declare dependencies as usual** (i.e. `implementation` and `api`).

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
    compileOnly "org.osgi:osgi.core:$osgiVersion"
    compileOnly "org.osgi:org.osgi.service.component.annotations:$osgiScrAnnotationVersion"

    runtimeOnly "org.apache.felix:org.apache.felix.scr:$felixScrVersion"
    runtimeOnly "org.osgi:org.osgi.util.function:$osgiUtilFunctionVersion"
    runtimeOnly "org.osgi:org.osgi.util.promise:$osgiUtilPromiseVersion"
}
```

Felix, embedded in the bootable JAR, exports the `org.osgi` packages to the bundles it activates.

[OSGi annotations](http://docs.osgi.org/specification/osgi.cmpn/7.0.0/service.component.html) are the preferred way
to declare services.
This documents uses annotations to explain
[how to define the entry point of an application](#how_to_define_the_entry_point_of_an_application) and
[how to refer to additional components in the class implementing the Application interface](#how_to_refer_to_additional_components_in_the_class_implementing_the_Application_interface).

### How to build an application

To build the bootable JAR, run the Gradle task

* `appJar` - builds the application module as OSGI bundle. It zips those dependencies are OSGi bundles and the bundle of
  the application itself in the bootable JAR. It defines the bootable JAR manifest to bootstrap Felix and activates all
  the bundles zipped.
  

### Logging

The plugin and the bootable JAR exposes the [SLF4J](http://www.slf4j.org/) and
[Apache Log4j 2](https://logging.apache.org/log4j/2.x/) packages to the bundles through the OSGi framework, using *Log4j
2* to implement logging.

In *Kotlin* code declare the `logger` as usual.

```kotlin
companion object {
    val logger = LoggerFactory.getLogger(MyClass::class.java)
}
```

or

```kotlin
val logger = contextLogger()
```

if the module depends on `net.corda:corda-base`.

The application logs in a single flow according the time of generations all events, either logged by OSGi bundles, Felix
or the code bootstrapping and controlling Felix.


### How to define the entry point of an application

Just one class must implement the `net.corda.osgi.api.Application` interface. This class must be an OSGi component to
register itself as an OSGi service.

Annotate the class implementing `net.corda.osgi.api.Application` with

```kotlin
@Component
```

*NOTE! In this document the expressions 'component' and OSGi 'service' are synonyms
because OSGi services are declared with the `@Component` annotation.*

See [OSGi Core r7 5.2.2 Service Interface](http://docs.osgi.org/specification/osgi.core/7.0.0/framework.service.html).

See [OSGi Compendium r7 112. Declarative Services Specification](https://docs.osgi.org/specification/osgi.cmpn/7.0.0/service.component.html).

See [OSGi Compendium r7 112.2.2 Immediate Component](https://docs.osgi.org/specification/osgi.cmpn/7.0.0/service.component.html#d0e36881).

The `net.corda.osgi.api.Application.startup(args: Array<String>)` is the entry-point of the application called when all
the OSGi bundles zipped in the bootable JAR are active.

If no class provides the `net.corda.osgi.api.Application` service, the application bootstrap throws an exception and
stops.

See [How to stop the application programmatically](#) for a full example of how to
implement `net.corda.osgi.api.Application`

*EXAMPLE*

```kotlin
@Component(service = [Application::class])
class App : Application {

    private companion object {

        private val logger: Logger = LoggerFactory.getLogger(App::class.java)
        
    }
    
    override fun startup(args: Array<String>) {
        logger.info("Start-up with args = $args.")
    }

    override fun shutdown() {
        logger.info("Shutdown.")
    }
}
```


### How to refer to additional components in the class implementing the Application interface

The class implementing the `net.corda.osgi.api.Application` is an OSGI service, thus a OSGi component, the
terms are synonyms.

To let the class to access additional services, annotate them with `@Reference` where the
`service` attribute is the full-qualified name used to publish the service.
By default, if the `service` attribute is not declared, the attribute is set to the type of the property annotated
with `@Reference`.

```kotlin
@Component(service = [Application::class])
class App : Application {

    private companion object {

        private val logger: Logger = LoggerFactory.getLogger(App::class.java)
        
    }

    @Reference // is like to annotate @Reference(service = InstallService::class)
    private var installService: InstallService? = null    

    @Reference // is like to annotate @Reference(service = SandboxService::class)
    private var sandboxService: SandboxService? = null
    
    override fun startup(args: Array<String>) {
        logger.info("Start-up with args = $args.")
    }

    override fun shutdown() {
        logger.info("Shutdown.")
    }
}
```

*NOTE! The `@Reference` annotation is ineffective if the class is not annotated as `@Component` as well.*

The following example declares `kafkaTopicAdmin` in the constructor to refers the service published as
the `KafkaTopicAdmin` service.

```kotlin
@Component(service = [Application::class])
class App @Activate constructor(
    @Reference(service = KafkaTopicAdmin::class)
    private var kafkaTopicAdmin: KafkaTopicAdmin,
) : Application {
    // ...
}
```

To use the `@Reference` annotation in the constructor, the constructor must be annotated with `@Activate`.

The referenced service must be annotated as `@Component` with an element of the `service` attribute set to the
same class used in the reference. Completing the above example, the `KafkaTopicAdmin` class must be annotated as

```kotlin
@Component(immediate = true, service = [KafkaTopicAdmin::class])
class KafkaTopicAdmin @Activate constructor(
    @Reference(service = TopicUtilsFactory::class)
    private val topicUtilsFactory: TopicUtilsFactory
) {
    // ...
}
```

As rule of thumbs, each time a property is annotated with `@Reference(service = <class>)`,
the `<class>` must be annotated with `@Component(service = [<class>])`.


### How to clean-up and release resources before to quit

The `net.corda.osgi.api.Application.shutdown()` method is called before the OSGi framework stops. Code here the
behaviour of the application to release resources and to clean-up before to stop.


### How to stop the application programmatically

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


### Testing

See [OSGi Test Conventions Plugin](#OSGi Test Conventions Plugin).


### Advanced dependency declarations and Gradle tasks

The **Common App** plugins allows developers to use the

To build a module as an OSGi bundle, the application depends on, use the [Common Libry Plugin](#Common Library Plugin)
.rara## Flow Worker Convention Plugins

These plugins are provided as a way to assist and ease the development of libraries and applications using the
flow-worker framework.


### The bootable JAR

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

## Common Library Plugin

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

## OSGi Test Conventions Plugin

The **OSGi Test Conventions** plugin provides tasks useful for running OSGi integration tests as well as some necessary
dependencies.

The tasks are as follows:

* `integrationTest` - executes the integration tests within an OSGi framework.

* `testingBundle` - compiles integration tests into an OSGi bundle which can be loaded.

* `resolve` - resolves all bundle dependencies and verifies them against the `test.bndrun` file.

* `testOSGi` - executes the integration tests within an OSGi framework.

* `check` - executes the integration tests within an OSGi framework.

## Publish Plugin

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

* `createSystemPackagesExtraFile` - to list in the file `system_packages_extra`
  the OSGi *Export-Package* instructions needed to export the dependencies declared with `systemPackages` through the
  OSGi framework to the bundles. The `systemPackages` dependencies are zipped in the bootable JAR by the `appJar` task.

* `createSystemBundleFile` - lists the required bundles in the
  file `system_bundles`.

See [The bootable JAR](#the_bootable_jar) section to know how is made the internal structure of the bootable JAR.

## Create Docker Image Custom Gradle Task
A custom Gradle task has been provided to allow for the containerization of deployable Jars in the Corda-runtime-os project.
An example of how to apply this task to a project can be seen in the applications/http-rpc-gateway application. 

``` groovy
tasks.register('publishOSGiImage', net.corda.gradle.DeployableContainerBuilder) {
    description "Builds the docker image for the deployable OSGi application"

    if (project.hasProperty('jibRemotePublish')) {
        remotePublish = jibRemotePublish.toBoolean()
    }

    if (project.hasProperty('releaseCandidate')) {
        releaseCandidate = releaseCandidate.toBoolean()
    }
}
```

Once triggered this task will produce a docker image tagging it with the following labels.
- latest
- the project version
- git revision

If ran locally with no Gradle properties passed the task will publish images to the local Docker Daemon. 

if jibRemotePublish is true images will be published to artifactory under:

    corda-os-docker-dev.software.r3.com/corda-os-${projectName}

CI builds will automatically publish to the remote repo.

Optionally an 'arguments' array may be provided to the task which will bake parameters into the image to be passed to the java -jar command.
Unless necessary this should be avoided and use environment variable JAVA_TOOL_OPTIONS to pass properties at run time instead, as follows:

    docker run -p 8888:8888 -e "JAVA_TOOL_OPTIONS=-DinstanceId=1" corda-os-docker-dev.software.r3.com/corda-os-http-rpc-gateway:latest

If a kafka.properties file exists in the project root as follows:

```
    http-rpc-gateway
        +--- src
        +--- build.gradle
        +--- kafka.properties
```

The file will be copied to the container and "--kafka", "/opt/pathToFile" will also be passed ot the java -jar command.
If this file does not exist in the project and therefore is never copied ot the container properties may be passed at container run time using JAVA_TOOL_OPTIONS as described previously.

    docker run -e  "JAVA_TOOL_OPTIONS=-Dconfig.topic.name=ConfigTopic,-Dmessaging.topic.prefix=http-rpc-gateway,-Dbootstrap.servers=localhost:9092"

### Running the container

```
docker run -it -p 8888:8888 -e "JAVA_TOOL_OPTIONS=-DinstanceId=1" corda-os-docker-dev.software.r3.com/corda-os-http-rpc-gateway:latest
```

### Debugging the container
To debug a running container we can use the JAVA_TOOL_OPTIONS environment variable to pass arguments at runtime e.g.

```
docker run -it -p 8888:8888 -p 5005:5005 -e "JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005 -DinstanceId=1"" corda-os-docker-dev.software.r3.com/corda-os-http-rpc-gateway:latest
```

__NOTE:__ `-p 5005:5005` which forwards internal container debug port to a local port such that remote debugger can
be attached.

__NOTE:__ Extra parameters can be passed to the java -jar command at run time by passing these within the JAVA_TOOL_OPTIONS environment variable.

    docker run -e "JAVA_TOOL_OPTIONS=<JVM flags>" <image name>
