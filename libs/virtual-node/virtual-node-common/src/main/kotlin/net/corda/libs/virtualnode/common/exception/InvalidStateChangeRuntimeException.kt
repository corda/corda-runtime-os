package net.corda.libs.virtualnode.common.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Indicates a requested state change matches the resources current state
 *
 * @param message the exception message
 */
class InvalidStateChangeRuntimeException(message: String) : CordaRuntimeException(message) {

    /**
     * @param resource The resource which the state effects
     * @param id The ID of the resource.
     */
    constructor(resource: String, state: String, id: String) : this("$resource '$id' is already in $state state.")
}