
# Test Corda application

This module implements `net.corda.osgi.api.Application` to run tests.

This module is the template to develop integration tests for this `flow-worker` project.

To build the JAR bootable application for this module, run

```shell
./gradlew :testing:apps:test-sandbox:clean :testing:apps:test-sandbox:appJar
```

To build the JAR bootable application integrated with **Flask* and **Quasar** agents for this module, run

```shell
./gradlew :testing:apps:test-sandbox:clean :testing:apps:test-sandbox:appFlask
```