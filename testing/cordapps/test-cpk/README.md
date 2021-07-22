# Test CPK *cordapp*

This module is a *cordapp* with minimal dependencies used to check this `flow-worker` project runs.

To build the CPK, run the OS shell

```shell
./gradlew :testing:cordapps:test-cpk:clean :testing:cordapps:test-cpk:cpk
```

The module `test-sandbox` implements `net.corda.osgi.api.Application` to run this `test-cpk` *cordapp*.

