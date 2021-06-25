
# Test Corda *sandbox* application

This module implements `net.corda.osgi.api.Application` to run the `test-cpk` *cordapp*.

This module is the template to develop integration tests for this `flow-worker1` project.

The module depends on [Corda 5](https://github.com/corda/corda5) implementations.
To build it, in the Corda 5 repository runs the Gradle task `publishToMavenLocal`.

```shell
./gradlew clean build
./gradlew publishToMavenLocal 
```

To build the JAR bootable application for this module, run

```shell
./gradlew :applications:examples:test-sandbox:clean :applications:examples:test-sandbox:appJar
```