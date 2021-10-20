# Applications

This module contains application code.

Applications should be a thin layer that composes some number of components/libraries, 
implements startup/specific logic etc. 

## Submodules

* `examples` module contains example applications
  * `goodbyeworld` module shows how an application uses the `net.corda.osgi.api.Application` interface to define
  the start-up and shut-down behavior of an application distributed as a bootable JAR.
  * `tools` module should contain tooling around the node, rather than node processes themselves.
  * `p2p-gateway` The P2P Gateway executable