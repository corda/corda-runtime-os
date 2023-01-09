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


### Change the log level

By default `log4j2-console.xml` configures log4j with a console appender outputting logs at `info` level but this can be overridden by setting environment variable `CONSOLE_LOG_LEVEL` on the container at startup.

`DeployableContainerBuilder.groovy` sets this on the JibContainerBuilder.

```groovy
builder.addEnvironmentVariable('CONSOLE_LOG_LEVEL', 'info')
```

### Enabling Log4j2 debug mode

Log4j2 debug mode can be enabled by setting environment variable `ENABLE_LOG4J2_DEBUG` on the container at startup.

`DeployableContainerBuilder.groovy` sets this on the JibContainerBuilder.

```groovy
builder.addEnvironmentVariable('ENABLE_LOG4J2_DEBUG', 'false')
```

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
This is applied once in the root build.gradle, any projects in applications:workers:release will automatically inherit this task.
Projects which require a docker image but are not in this location should update the array variable nonWorkerImages also in root build.gradle

``` groovy
        tasks.register('publishOSGiImage', net.corda.gradle.DeployableContainerBuilder) {
            if (project.hasProperty('jibRemotePublish')) {
                remotePublish = jibRemotePublish.toBoolean()
            }

            if (project.hasProperty('isReleaseCandidate')) {
                releaseCandidate = isReleaseCandidate.toBoolean()
            }

            if (project.hasProperty('isNightly')) {
                nightlyBuild = isNightly.toBoolean()
            }

            if (project.hasProperty('isPreTest')) {
                preTest = isPreTest.toBoolean()
            }
        }
```

Depending on where this task is triggered from it will produce different docker tags, for full details see design doc here
https://github.com/corda/platform-eng-design/blob/master/core/corda-5/corda-5.1/build-Infrastructure/overview.md#Containerization

If ran locally the task will produce an image tagged :latest-local along with the version number

If ran locally with no Gradle properties passed the task will publish images to the local Docker Daemon. 

CI builds will automatically publish to the remote repo tagging is as follows for various build types

```
Release Tag Builds
-Repo:corda-os-docker-stable
-Tags: 
    latest
    Version

Release branch builds 'main'
-Repo:corda-os-docker-unstable
-Tags: 
    unstable
    Version
    ShortGitHash

Feature branchs / PRs
-Repo:corda-os-docker-dev
-Tags: 
    version
    ShortGitHash

Nightlys 'main' branch
-Repo:corda-os-docker-nightly
-Tags: 
    nightly
    nightly-date


Nightlys feature branch
-Repo:corda-os-docker-nightly
-Tags: 
    nightly-jiraTicket
    nightly-jiraTicket-date
    nightly-jiraTickt-ShortGitHash

Pre test (to be used in infrastructure based testing in pipelines)
-Repo:corda-os-docker-pre-test
-Tags: 
    preTest-version
    preTest-ShortGitHash

Local Machine
-Repo:corda-os-docker-dev (wont be pushed)
-Tags: 
    latest-local
    Version
    ShortGitHash
```

For information how to run Workers locally, please see [here](../applications/workers/release/deploy/README.md).

## Creating Docker Images using Buildkit

Another task have been provided to build docker images. The `publishBuildkitImage` task inherits most of it’s functionality and structure from `publishOSGiImage`, but also provides better caching and speed to the builds. It uses BuildKit to create and publish docker images to specified repositories. 

Depending on which way the buildkit is used, the task can be run in two ways:

### Docker buildx
This version of buildkit has been integrated into docker and comes preinstalled with docker engine. This way is more favourable to developers as it requires no initial setup and provides most of the preferred functionality, mainly local caching of image layers.

### Buildctl with dedicated buildkit daemon
The standalone buildkit client buildctl provides the same functionality as buildx but also uses remote cache. The build is run through a buildkit daemon available on `eks-e2e` cluster. 

To use standalone buildkit, it's client buildctl needs to be installed.

For Mac the client can be installed using homebrew

```
brew install buildkit
```

Otherwise, it can be installed from source: 

```
git clone git@github.com:moby/buildkit.git buildkit
cd buildkit
make && sudo make install
```

The standalone buildkit requires a remote buildkit daemon to run. To connect to a buildkit daemon, developer has to log into `eke-e2e` cluster and port forward the daemon to port 3465.

```
aws --profile "${AWS_PROFILE}" eks update-kubeconfig --name eks-e2e
kubectl port-forward deployment/buildkit 3476:3476
```

Only after buildctlis installed and buildkit daemon is connected, publishBuildkitImage task can be used with standalone buildkit by setting the  useBuildx parameter to false.

```
 gradlew publishBuildkitImage -PuseBuildkit=false
```


