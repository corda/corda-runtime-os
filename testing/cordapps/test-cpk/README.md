# Test CPK *cordapp*

This module is a *cordapp* with minimal dependencies used to check this `flow-worker` project runs.

To build the CPK, run the OS shell

```shell
./gradlew :applications:examples:test-cpk:clean :applications:examples:test-cpk:cpk
```

The module `test-sandbox` implements `net.corda.osgi.api.Application` to run this `test-cpk` *cordapp*.

