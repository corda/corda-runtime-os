# Applications

This module contains application code.

Applications should be a thin layer that composes some number of components/libraries, 
implements startup/specific logic etc. 

## Submodules

* `examples` module contains example applications
  * `goodbyeworld` module shows how an application uses the `net.corda.osgi.api.Application` interface to define
  the start-up and shut-down behavior of an application distributed as a bootable JAR.
  * `test-cpk` module is a *cordapp* with minimal dependencies used to check this `flow-worker` project runs.
  * `test-sandbox` module implements `net.corda.osgi.api.Application` to run the `test-cpk` *cordapp*.
    This module is the template to develop ntegration tests for this `flow-worker1` project.
  * `tools` module should contain tooling around the node, rather than node processes themselves.