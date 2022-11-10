package net.corda.simulator.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

class NonImplementedAPIException(api: String) : CordaRuntimeException(
    "$api is not implemented in simulator for this release"
)